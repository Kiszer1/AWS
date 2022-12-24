import clients.awsSqsClient;
import com.asprise.ocr.Ocr;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SqsException;

import utilities.managerTask;
import utilities.names;

import java.net.MalformedURLException;
import java.net.URL;

public class worker {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Ocr ocr = new Ocr();
    private static final awsSqsClient sqs = new awsSqsClient();

    private String preformOcr(String url) {
        String imageOCR;
        try {
            URL[] urlList = new URL[1];
            urlList[0] = new URL(url);
            imageOCR = ocr.recognize(urlList, Ocr.RECOGNIZE_TYPE_TEXT, Ocr.OUTPUT_FORMAT_PLAINTEXT);
        } catch (MalformedURLException e) {
            imageOCR = "Bad URL";
        }
        if (imageOCR.isEmpty()) {
            imageOCR = "Ocr returned an empty string";
        }
        return imageOCR;
    }

    public void run() {
        Ocr.setUp();
        ocr.startEngine("eng", Ocr.SPEED_FAST);
        while (true){
            try {
                Message msg = sqs.getFromQueueBlocking(names.MANAGER_SEND_QUEUE, false);
                managerTask managerTask = mapper.readValue(msg.body(), utilities.managerTask.class);
                String output;
                try {
                    output = preformOcr(managerTask.getUrl());
                }catch (Exception e) {
                    managerTask.setOutput(e.getMessage());
                    sqs.sendToQueue(names.MANAGER_RECEIVE_QUEUE, mapper.writeValueAsString(managerTask));
                    sqs.deleteFromQueue(names.MANAGER_SEND_QUEUE, msg);
                    continue;
                }
                managerTask.setOutput(output);
                sqs.sendToQueue(names.MANAGER_RECEIVE_QUEUE, mapper.writeValueAsString(managerTask));
                sqs.deleteFromQueue(names.MANAGER_SEND_QUEUE, msg);
            } catch (SqsException e) {
                System.err.println(e.awsErrorDetails().errorMessage());
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new worker().run();
    }
}
