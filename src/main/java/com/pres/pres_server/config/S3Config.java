package com.pres.pres_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(prefix = "spring.cloud.aws.s3", name = "bucket")
public class S3Config {

        @Value("${aws.access-key}")
        private String accessKey;

        @Value("${aws.secret-key}")
        private String secretKey;

        @Value("${spring.cloud.aws.s3.bucket}")
        private String bucket;

        @Value("${spring.cloud.aws.region.static}")
        private String region;

        @Bean
        public S3Client s3Client() {
                return S3Client.builder()
                                .credentialsProvider(
                                                StaticCredentialsProvider.create(
                                                                AwsBasicCredentials.create(accessKey, secretKey)))
                                .region(Region.of(region))
                                .build();
        }

}