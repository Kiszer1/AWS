package clients;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import sun.security.krb5.internal.crypto.Des;
import utilities.names;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;


public class awsEc2Client {
    private final static Region region = Region.US_WEST_2;
    private final static Ec2Client ec2 = Ec2Client.builder()
            .region(region)
            .build();

    public Instance getInstanceByID(String instanceID) {
        DescribeInstancesRequest instancesRequest = DescribeInstancesRequest.builder()
                .instanceIds(instanceID)
                .build();
        DescribeInstancesResponse instancesResponse = ec2.describeInstances(instancesRequest);
        for (Reservation reservation : instancesResponse.reservations()) {
            for (Instance instance : reservation.instances()) {
                return instance;
            }
        }
        return null;
    }

    public List<Instance> getInstances() throws Ec2Exception {
        List<Instance> instances = new ArrayList<>();
        String nextToken = null;
        do {
            DescribeInstancesRequest instancesRequest = DescribeInstancesRequest.builder()
                    .nextToken(nextToken)
                    .build();
            DescribeInstancesResponse instancesResponse = ec2.describeInstances(instancesRequest);
            for (Reservation reservation : instancesResponse.reservations()) {
                for (Instance instance : reservation.instances()) {
                    instances.add(instance);
                }
            }
            nextToken = instancesResponse.nextToken();
        }   while(nextToken != null);
        return instances;
    }

    // getting all manager instances, some might be terminated.
    public List<Instance> getSpecificInstances(String name) throws Ec2Exception {
        return getInstances()
                .stream()
                .filter(
                        instance -> instance
                                .tags()
                                .stream()
                                .filter(tag -> tag.key().equals("tag"))
                                .anyMatch(tag -> tag.value().equals(name)))
                .collect(Collectors.toList());
    }

    public void terminateInstance(String instanceID) {
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceID)
                .build();
        ec2.terminateInstances(terminateRequest);
    }

    public List<Instance> createInstance(int instances, String script) {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
        IamInstanceProfileSpecification profile = IamInstanceProfileSpecification.builder()
                .name("LabInstanceProfile")
                .build();
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .minCount(instances)
                .maxCount(instances)
                .imageId(names.AMI_ID)
                .instanceType(names.INSTANCE_TYPE)
                .securityGroupIds(names.SECURITY_KEY)
                .keyName(names.INSTANCE_KEY)
                .iamInstanceProfile(profile)
                .userData(encoded)
                .build();
        RunInstancesResponse runResponse = ec2.runInstances(runRequest);
        return runResponse.instances();
    }

    public void createInstanceTags (String instanceID, String val) {
        Tag tag = Tag.builder()
                .key("tag")
                .value(val)
                .build();
        CreateTagsRequest tagsRequest = CreateTagsRequest.builder()
                .resources(instanceID)
                .tags(tag)
                .build();
        ec2.createTags(tagsRequest);
    }
}
