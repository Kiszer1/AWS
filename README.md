* Distributed System Programming: Scale Out with Cloud Computing.

* Made by:

Yaad Ben Moshe

* Running the App:
1. Place aws credentials in ~/.aws/credentials
2. Both worker java .jar and manager java .jar already uploaded to script buckets.
3. Place input file InputFileName in the same directory as the localApp.jar
4. Run the local app : java -jar localApp.jar inputFileName outputFileName n [terminate]
4. Wait for app to finish, the app will create an output file OutputFileName in the same directory as the localApp.jar
5. Additionaly 2 files will be created: LocalApp-Log.log and Manager-Log.log which are logs for the local App and Manager 

* Process:

** Local App:
1. Starts by taking command line arguments and making an appTask.
2. Creates a unique queue for getting messages from the mannager.
3. If they do not already exsist: creates a queue for sending tasks to the manager, 2 Buckets, 1 for sending files to manage and 1 to recieve files back.
4. Checks if there is an instance with name tag "manager", if not the app wil start a new one.
5. Sends the appTask to the manager using the queue.
6. Waits for completion.
7. Gets the summary file created by the manager from S3, deleting it once recieved.
8. Creates an HTML file with the output file name given.
9. Destroys the unique queue it has created.
10. If the app terminated the manager, it will download the manager log file from S3.

** Manager:
1. If they do not already exsist: created 2 queues, one for sending messages and one for recieving messages from / to Workers.
2. Initiates a Thread "waiter thread" to run in an infinite loop waiting for workers messages containing a finished managerTask.
   1. Upon getting a finished managerTask from the queue, the waiter thread will track which appTask it belongs to and update it.
   2. If the appTask is complete, meaning all managerTask created from it are finished, the waiter thread with start a new Thread to finish the task. The waiter thread will go back 
      to waiting on messages.
   3. The task finisher thread will create a new file, writes the answers of each managerTask to the file, saves it on S3 and sends a queue message to the app that the appTask is done.
3. Runs in an infinite loop waiting for appTasks.
4. Once it recieves an appTask it starts a thread to the process the task, and goes waiting on addiotional appTasks.
   1. The processing thread will get the file specified in the appTask from S3 and delete it from S3, then it will calculate the needed workers for the appTask.
   2. The processing thread will then start k - m workers, where k is the amount of workers needed for the task and m is the number of running workers.
   3. Upon starting the workers for the first time, it will initiate a thread which will constantly check that all worker instances are running.
   4. Using the file it got from S3, it will create managerTasks and send them to a queue for workers to process. 
   5. if a termination request been given and all appTasks are completed, the finisher thread will notify the termination thread.
5. If the appTask has a termination request, the manager will reject any new appTasks, replying to apps with an "already terminated" response. It will start a termination thread, which will wait on all appTasks to be
finished and then: terminate all worker instance, upload the manager log to S3 and lastly terminate manager instance.

** Worker:
1. Starts the Ocr engine.
2. Runs in an infinite loop waiting for managerTasks. 
3. After it recieves a managerTask from the queue, it will create a URL and preform Ocr on it.
4. Once the Ocr analysis is done, the worker will update the managerTask and send it back to the manager.
5. If the Ocr failed, or an empty string was returned from it, the worker will note it in the managerTask instead of the text.
6. If the message was sent back to the manager, the worker will delete the message from the queue, to comply with the visibilaty timeout.



* Using the sample input of 24 images with n = 5 (5 workers) we got:
   * Run time with a manager and workers not online: 2 minutes and 25 seconds.
   * Run time with a manager and workers online and waiting: 43 seconds.

* AMI used: ami-0685d44ae00985837 Made by Yaad and Nitzan.

* Used a logger to log manager and the local app.

* All output files will be at the same directory as the jar.

* Instance type used: T2.Micro
