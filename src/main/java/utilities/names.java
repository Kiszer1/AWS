package utilities;

import software.amazon.awssdk.services.ec2.model.InstanceType;

public class names {
    public static final String APP_SEND_QUEUE = "yaad-nitzan-app-send-queue";
    public static final String APP_RECEIVE_QUEUE = "yaad-nitzan-app-return-queue";
    public static final String APP_SEND_BUCKET = "yaad-nitzan-app-send-bucket";
    public static final String APP_RECEIVE_BUCKET = "yaad-nitzan-app-return-bucket";
    public static final String LOG_BUCKET = "yaad-nitzan-log-bucket";

    public static final String INSTANCE_KEY = "Yaad-Nitzan-key";
    public static final String SECURITY_KEY = "sg-02a98db6e2d685095";
    public static final String AMI_ID = "ami-0685d44ae00985837";

    public static final String MANAGER_SEND_QUEUE = "yaad-nitzan-manager-send-queue";
    public static final String MANAGER_RECEIVE_QUEUE = "yaad-nitzan-manager-return-queue";

    public static final InstanceType INSTANCE_TYPE = InstanceType.T2_MICRO;


    public static String script(String instanceID) {
        String script = "#!/bin/bash\n";
        script += String.format("aws s3 cp s3://yaad-nitzan-script-bucket/%s.jar .\n", instanceID);
        script += String.format("java -jar %s.jar\n", instanceID);
        return script;
    }
}

