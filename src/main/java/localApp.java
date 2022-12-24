import clients.awsEc2Client;
import clients.awsS3Client;
import clients.awsSqsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;

import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sqs.model.SqsException;

import utilities.appTask;
import utilities.names;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class localApp {

    private static String outputFileName = "final.html";
    private static final long randomID = System.currentTimeMillis();
    private static final awsEc2Client ec2 = new awsEc2Client();
    private static final awsS3Client s3 = new awsS3Client();
    private static final awsSqsClient sqs = new awsSqsClient();

    private static final appTask appTask = new appTask();
    ObjectMapper mapper = new ObjectMapper();

    public static final Logger diary = Logger.getLogger(localApp.class);

    private boolean checkActive(InstanceStateName name) {
        return (name == InstanceStateName.PENDING || name == InstanceStateName.RUNNING);
    }

    private void startManager() {
        Instance manager = ec2.getSpecificInstances("MANAGER")
                .stream()
                .filter(instance -> checkActive(instance.state().name()))
                .findAny()
                .orElse(null);
        if (manager == null) {
            diary.info("No manager running, Starting manager");
            manager = ec2.createInstance(1, names.script("manager")).get(0);
            ec2.createInstanceTags(manager.instanceId(),"MANAGER");
        }
    }

    public void create() throws S3Exception, SqsException {
        sqs.createQueue(names.APP_RECEIVE_QUEUE + randomID);
        sqs.createQueue(names.APP_SEND_QUEUE);
        s3.createBucket(names.APP_SEND_BUCKET);
        s3.createBucket(names.APP_RECEIVE_BUCKET);
    }

    private void createHTML(BufferedReader buffer) throws IOException{
        diary.info("Creating final HTML file");
        File ocrHTML = new File(outputFileName);
        FileWriter writer = new FileWriter(ocrHTML);
        writer.write("<html><head><title>OCR</title></head><body>");
        String line = buffer.readLine();
        while (line != null) {
            writer.write("<p>" + line + "</p>\n");
            line = buffer.readLine();
        }
        writer.close();
    }

    public void run(File inputFile) throws IOException{
        try {
            create();
        } catch (S3Exception e) {}

        System.out.println("Starting manager");
        startManager();
        diary.info("Started manager");
        s3.putObject(names.APP_SEND_BUCKET, appTask.getFileName(), inputFile);
        System.out.println("App added to bucket");
        diary.info("Uploaded file to bucket");
        try {
            sqs.sendToQueue(names.APP_SEND_QUEUE, mapper.writeValueAsString(appTask));
            System.out.println("App sent message to manager");
            diary.info("Sent task to manager");
            String managerMsg = sqs.getFromQueue(names.APP_RECEIVE_QUEUE + randomID);
            if (managerMsg.equals("Already Terminated")) {
                System.out.println("Manager already terminated, shutting down");
                diary.info("Manager already terminated, shutting down");
                System.exit(1);
            }
            System.out.println("App got message back - manager should be done: " + managerMsg);
            diary.info("Manager finished the task");
            BufferedReader buffer = s3.getObject(names.APP_RECEIVE_BUCKET, "managerResult" + randomID);
            System.out.println("App got bucket result");
            diary.info("Got output file from manager");
            s3.deleteObject(names.APP_RECEIVE_BUCKET, "managerResult" + randomID);
            System.out.println("App deleted object from bucket");
            diary.info("Deleted file from bucket");
            createHTML(buffer);
            System.out.println("App created HTML");
            diary.info("Finished creating HTML file");
        } finally {
            sqs.deleteQueue(names.APP_RECEIVE_QUEUE + randomID);
            System.out.println("App deleted queue");
            diary.info("Deleting Queue: " + names.APP_RECEIVE_QUEUE + randomID);
        }
        System.out.println("App Done - DONE");
        diary.info("App Done\nFile: " + outputFileName);
    }

    private void setJson(String fileName, int n, boolean terminate) {
        appTask.setFileName(fileName);
        appTask.setN(n);
        appTask.setTerminate(terminate);
        appTask.setRandomID(randomID);
    }

    private void clean() {
        sqs.deleteQueue(names.APP_SEND_QUEUE);
        sqs.deleteQueue(names.MANAGER_SEND_QUEUE);
        sqs.deleteQueue(names.MANAGER_RECEIVE_QUEUE);
    }



    public static void main(String[] args) throws IOException {
        if (args.length < 3)
            return;
        File inputFile = new File(args[0]);
        outputFileName = args[1];
        int n = Integer.parseInt(args[2]);
        if (n == 0) {
            return;
        }
        boolean terminate = args.length > 3 && args[3].equals("terminate");
        System.out.println("Terminating?: " + terminate);

        diary.info("Local app started with command line arguments:");
        localApp app = new localApp();
        app.setJson(inputFile.getName(), n, terminate);
        diary.info("Command line arguments set:\nInput file: " + args[0] + "\nOutput file: " + args[1] + "\nn: " + n + "\nTerminate:" + terminate);

        app.run(inputFile);

        if (terminate) {
            System.out.println("Getting manager log");
            try {
                Runtime.getRuntime().exec("aws s3 cp s3://" + names.LOG_BUCKET + "/Manager-log" + randomID + ".log .\n");
                diary.info("Got manager log");
            } catch (S3Exception e) {
                diary.error("Failed to get manager log, error: " + e.awsErrorDetails().errorMessage());
            }
        }

    }
}

