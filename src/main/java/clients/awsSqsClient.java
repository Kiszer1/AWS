package clients;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.List;

public class awsSqsClient {
    final static Region region = Region.US_WEST_2;
    final static SqsClient sqs = SqsClient.builder().region(region).build();

    public void createQueue(String queue) throws SqsException {
        CreateQueueRequest createRequest = CreateQueueRequest.builder()
                .queueName(queue)
                .build();
        sqs.createQueue(createRequest);
    }

    public String getQueueUrl(String queue) throws SqsException {
        GetQueueUrlRequest urlRequest = GetQueueUrlRequest.builder()
                .queueName(queue)
                .build();
        return sqs.getQueueUrl(urlRequest).queueUrl();
    }

    public void deleteQueue(String queue) throws SqsException {
        DeleteQueueRequest deleteRequest = DeleteQueueRequest.builder()
                .queueUrl(getQueueUrl(queue))
                .build();
        sqs.deleteQueue(deleteRequest);
    }

    public void sendToQueue(String queue, String msg) throws SqsException {
        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(getQueueUrl(queue))
                .messageBody(msg)
                .build();
        sqs.sendMessage(sendRequest);
    }

    public String getFromQueue(String queue) throws SqsException {
        return getFromQueueBlocking(queue, true).body();
    }

    public Message getFromQueueBlocking(String queue, boolean delete) throws SqsException {
        List<Message> messages;
        do {
            ReceiveMessageRequest getRequest = ReceiveMessageRequest.builder()
                    .maxNumberOfMessages(1)
                    .visibilityTimeout(20)
                    .queueUrl(getQueueUrl(queue))
                    .build();
            messages = sqs.receiveMessage(getRequest).messages();
        } while (messages.isEmpty());
        if (delete)
            deleteFromQueue(queue, messages.get(0));
        return messages.get(0);
    }

    public void deleteFromQueue(String queue, Message msg) throws SqsException {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(getQueueUrl(queue))
                .receiptHandle(msg.receiptHandle())
                .build();
        sqs.deleteMessage(deleteRequest);
    }
}
