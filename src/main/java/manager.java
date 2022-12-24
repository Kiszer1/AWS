import clients.awsEc2Client;
import clients.awsS3Client;
import clients.awsSqsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import utilities.appTask;
import utilities.managerTask;
import utilities.names;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class manager {
    private static boolean started = false;
    private static int tasksGot = 0;
    private static int tasksFinished = 0;
    private static boolean terminate = false;
    private static long terminatingApp;
    private static int workers = 0;

    private static final awsEc2Client ec2 = new awsEc2Client();
    private static final awsS3Client s3 = new awsS3Client();
    private static final awsSqsClient sqs = new awsSqsClient();

    private static final ObjectMapper mapper = new ObjectMapper();
    private static Map<String, Instance> workerInstances = new ConcurrentHashMap<>();
    private static Map<Long, appTask> appTasks = new ConcurrentHashMap<>();
    private static Map<Long, ArrayList<managerTask>> completedTasks = new ConcurrentHashMap<>();

    private static final Object terminationNotice = new Object();

    public static final Logger diary = Logger.getLogger(manager.class);

    private boolean checkActive(InstanceStateName name) {
        return (name == InstanceStateName.PENDING || name == InstanceStateName.RUNNING);
    }

    public void waitForTasks () {
        synchronized (terminationNotice) {
            while (tasksGot != tasksFinished) {
                try {
                    terminationNotice.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            terminate();
        }
    }

    private void terminate() {
        diary.info("Manager started terminating workers");
        Instance manager = ec2.getSpecificInstances("MANAGER")
                .stream()
                .filter(instance -> checkActive(instance.state().name()))
                .findAny()
                .get();
        // Terminating workers.
        for (Instance instance : workerInstances.values()) {
            try {
                ec2.terminateInstance(instance.instanceId());
                diary.info("Manager terminated worker: " + instance.instanceId());
            } catch (Ec2Exception ex) {
                diary.fatal("Could not terminate instance: MANUAL TERMINATION NEEDED!!.\n Instance ID: " + instance.instanceId());
            }
        }
        // Terminating Self
        diary.info("Manager DONE, terminating.");
        File log = new File("Manager-log.log");
        s3.putObject(names.LOG_BUCKET, "Manager-log" + terminatingApp + ".log", log);
        ec2.terminateInstance(manager.instanceId());
        System.exit(0);
    }


    private void processThread(Runnable program) {
        program.run();
    }

    private synchronized void startWorkers(int numOfWorkers) {
        diary.info("Manager starting workers, need: " + (numOfWorkers - workers));
        if (workers < numOfWorkers) {
            List<Instance> newWorkers = ec2.createInstance(numOfWorkers - workers, names.script("worker"));
            for (Instance instance : newWorkers) {
                ec2.createInstanceTags(instance.instanceId(), "worker" + workers++);
                workerInstances.put(instance.instanceId(), instance);
                diary.info("Manager created worker: " + (workers - 1));
            }
        }
        if (!started) {
            started = true;
            Thread workerFail = new Thread(() -> processThread(this::checkFail));
            workerFail.start();
            diary.info("Manager started checking for worker failures");
        }
    }

    private void processAppTask(appTask appTask)  {

        BufferedReader buffer = s3.getObject(names.APP_SEND_BUCKET, appTask.getFileName());
        diary.info("Manager got file from bucket for task: " + appTask.getRandomID());
        s3.deleteObject(names.APP_SEND_BUCKET, appTask.getFileName());
        diary.info("Manager deleted file from bucket for task: " + appTask.getRandomID());
        List<String> tasks = buffer.lines().collect(Collectors.toList());

        appTask.amount = tasks.size();
        appTasks.put(appTask.getRandomID(), appTask);
        completedTasks.put(appTask.getRandomID(), new ArrayList<>());
        int extra = (appTask.amount % appTask.getN() > 0) ? 1 : 0;
        int workersForTask = Math.min(5, (appTask.amount / appTask.getN()) + extra);
        diary.info("Task: " + appTask.getRandomID() + " Size: " + appTask.amount + " Workers needed: " + workersForTask);
        startWorkers(workersForTask);

        managerTask task = new managerTask();
        task.setRandomID(appTask.getRandomID());

        for (String taskUrl : tasks) {
            task.setUrl(taskUrl);
            try {
                sqs.sendToQueue(names.MANAGER_SEND_QUEUE, mapper.writeValueAsString(task));
                diary.info("Manager sent managerTask to queue, for task: " + appTask.getRandomID());
            } catch (Exception e){
                diary.error(e.getMessage());
            }
        }
    }

    public void finishTask(ArrayList<managerTask> tasks, long randomID) {
        try {
            File outputFile = new File("output-raw" + randomID);
            FileWriter writer = new FileWriter(outputFile);
            for (managerTask task : tasks) {
                writer.write(task.getUrl() + "\n" + task.getOutput() + "\n");
            }
            writer.close();
            diary.info("Manager finished writing raw output for task: " + randomID);
            s3.putObject(names.APP_RECEIVE_BUCKET, "managerResult" + randomID, outputFile);
            diary.info("Manager put finished file: " + randomID + " in bucket");
            sqs.sendToQueue(names.APP_RECEIVE_QUEUE + randomID, "Jobs done");
            diary.info("Manager sent back Jobs done message to app: " + randomID);
            tasksFinished++;

            if (tasksGot == tasksFinished && terminate)
                synchronized (terminationNotice) {
                    workers = 0;
                    diary.info("Manager finished all tasks, notifying terminator");
                    terminationNotice.notifyAll();
                }

        } catch (IOException e) {
            diary.error(e.getMessage());
        }
    }

    public void waitForAnswers() {
        while(true) {
            try {
                String msg = sqs.getFromQueue(names.MANAGER_RECEIVE_QUEUE);
                managerTask task = mapper.readValue(msg, managerTask.class);
                diary.info("Manager received an answer for task: " + task.getRandomID());
                ArrayList<managerTask> list = completedTasks.get(task.getRandomID());
                list.add(task);
                if (appTasks.get(task.getRandomID()).amount == list.size()) {
                    diary.info("Manager got all answers for task: " + task.getRandomID());
                    Thread taskFinisher = new Thread(() -> processThread(() -> finishTask(list, task.getRandomID())));
                    taskFinisher.start();
                    diary.info("Manager finalising task: " + task.getRandomID());
                }
            } catch (Exception e) {
                diary.error(e.getMessage());
            }
        }
    }

    public void checkFail() {
        Instance toRemove = null;
        Instance toAdd = null;
        while (workers > 0) {
            try {
                for (Instance instance : workerInstances.values()) {
                    if (!checkActive(ec2.getInstanceByID(instance.instanceId()).state().name())) {
                        diary.warn("Manager found a worker not working, replacing instance with ID: " + instance.instanceId());
                        ec2.terminateInstance(instance.instanceId());
                        List<Instance> newWorker = ec2.createInstance(1, names.script("worker"));
                        ec2.createInstanceTags(newWorker.get(0).instanceId(), "worker" + workers++);
                        diary.warn("Manager replaced worker: " + (workers - 1)  + " with worker: " + workers);
                        toRemove = instance;
                        toAdd = newWorker.get(0);
                        break;
                    }
                }
                if (toRemove != null && toAdd != null) {
                    workerInstances.remove(toRemove.instanceId());
                    workerInstances.put(toAdd.instanceId(), toAdd);
                    toRemove = null;
                    toAdd = null;
                }
            } catch (Ec2Exception e) {
                diary.error("Manager got an error while checking for worker failures: " + e.awsErrorDetails().errorMessage());
            }
        }
    }

    public void create() {
        sqs.createQueue(names.MANAGER_SEND_QUEUE);
        sqs.createQueue(names.MANAGER_RECEIVE_QUEUE);

        Thread incomingMessages = new Thread(() -> processThread(this::waitForAnswers));
        incomingMessages.start();
        diary.info("Manager started waiting for answers from workers");
    }

    public void run() {
        create();
        System.out.println("Manager created");
        diary.info("Manager started running");
        while(true) {
            try {
                String message = sqs.getFromQueue(names.APP_SEND_QUEUE);
                appTask appTask = mapper.readValue(message, appTask.class);
                diary.info("Manager received a task: " + appTask.getRandomID());
                if (terminate) {
                    diary.info("Manager already terminating, not completing task: " + appTask.getRandomID());
                    sqs.sendToQueue(names.APP_RECEIVE_QUEUE +appTask.getRandomID(), "Already Terminated");
                    diary.info("Sent back already terminated message");
                    continue;
                }
                tasksGot ++;
                terminate = appTask.isTerminate();
                Thread processor = new Thread(() -> processThread(() -> processAppTask(appTask)));
                processor.start();
                diary.info("Manager started processing task: " + appTask.getRandomID());

                if (terminate) {
                    terminatingApp = appTask.getRandomID();
                    diary.info("Manager received a termination order from: " + terminatingApp);
                    Thread terminate = new Thread(this::waitForTasks);
                    terminate.start();
                    diary.info("Manager started termination process, waiting on prior tasks");
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                diary.error(e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new manager().run();
    }

}
