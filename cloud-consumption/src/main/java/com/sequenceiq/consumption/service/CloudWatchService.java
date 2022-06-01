package com.sequenceiq.consumption.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

import com.sequenceiq.cloudbreak.cloud.aws.AwsCloudFormationClient;
import com.sequenceiq.cloudbreak.cloud.aws.common.client.AmazonCloudWatchClient;
import com.sequenceiq.cloudbreak.cloud.aws.common.view.AwsCredentialView;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;


import org.springframework.stereotype.Service;

@Service
public class CloudWatchService {

    private final String bucketNameDimension="BucketName";

    private final String storageType="StandardStorage";

    private final String storageTypeDimension="StorageType";

    private final String namespace="AWS/S3";

    private final String metricName="BucketSizeBytes";

    private final String statisticsType="Maximum";

    private final String unit="Bytes";

    private final int period=3600;
    @Inject
    private AwsCloudFormationClient awsClient;

    public GetMetricStatisticsResult getMetricsStatistics(CloudCredential cloudcredential, String region, String metricName, String namespace, Date startTime, Date endTime,String statisticsType,
            String unit, List<Dimension> dimensions, Integer period ){
        AwsCredentialView credentialView = new AwsCredentialView(cloudcredential);
        AmazonCloudWatchClient amazonCloudWatchClient = awsClient.createCloudWatchClient(credentialView, region);
        GetMetricStatisticsRequest getMetricStatisticsRequest = new GetMetricStatisticsRequest()
                .withPeriod(period)
                .withMetricName(metricName)
                .withUnit(unit)
                .withNamespace(namespace)
                .withStartTime(startTime)
                .withEndTime(endTime)
                .withStatistics(statisticsType)
                .withDimensions(dimensions);
        GetMetricStatisticsResult getMetricStatisticsResult = amazonCloudWatchClient.getMetricStatisticsResult(getMetricStatisticsRequest);
        return getMetricStatisticsResult;
    }

    public GetMetricStatisticsResult getBucketSize(CloudCredential cloudCredential,String region, Date startTime, Date endTime,String bucketName){
          Dimension bucketDimension = new Dimension().withName(bucketNameDimension).withValue(bucketName);
          Dimension storageDimension = new Dimension().withName(storageTypeDimension).withValue(storageType);
        List<Dimension> dimensionList = new ArrayList<Dimension>(Arrays.asList(bucketDimension, storageDimension));
          GetMetricStatisticsResult result= getMetricsStatistics(cloudCredential,region,metricName,namespace,startTime,endTime,statisticsType,unit,dimensionList,period);
          return result;
    }

}
