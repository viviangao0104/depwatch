package com.depwatch.common;

import com.depwatch.common.security.UserContextFilter;
import com.depwatch.common.storage.LocalManifestStorage;
import com.depwatch.common.storage.ManifestStorage;
import com.depwatch.common.storage.S3ManifestStorage;
import com.depwatch.common.storage.StorageProperties;
import com.depwatch.common.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 通过 META-INF/spring/...AutoConfiguration.imports 自动装配，
 * 三个 Servlet 服务引入 common 依赖即可获得：用户上下文过滤器、统一异常处理、manifest 存储。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(StorageProperties.class)
@Import(GlobalExceptionHandler.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean(ManifestStorage.class)
    @ConditionalOnProperty(prefix = "depwatch.storage", name = "type", havingValue = "local", matchIfMissing = true)
    public ManifestStorage localManifestStorage(StorageProperties props) throws IOException {
        return new LocalManifestStorage(Path.of(props.getLocal().getRoot()));
    }

    @Bean
    @ConditionalOnMissingBean(ManifestStorage.class)
    @ConditionalOnProperty(prefix = "depwatch.storage", name = "type", havingValue = "s3")
    public ManifestStorage s3ManifestStorage(StorageProperties props) {
        S3Client client = S3Client.builder()
                .region(Region.of(props.getS3().getRegion()))
                .build();
        return new S3ManifestStorage(client, props.getS3().getBucket(), props.getS3().getPrefix());
    }
}
