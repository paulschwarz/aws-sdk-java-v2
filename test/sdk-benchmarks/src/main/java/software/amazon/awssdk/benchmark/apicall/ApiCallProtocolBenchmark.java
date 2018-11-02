/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.benchmark.apicall;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static software.amazon.awssdk.benchmark.utils.BenchmarkUtils.JSON_BODY;
import static software.amazon.awssdk.benchmark.utils.BenchmarkUtils.PORT_NUMBER;
import static software.amazon.awssdk.benchmark.utils.BenchmarkUtils.XML_BODY;
import static software.amazon.awssdk.benchmark.utils.BenchmarkUtils.getUri;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import software.amazon.awssdk.benchmark.utils.BenchmarkUtils;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.protocolec2.ProtocolEc2Client;
import software.amazon.awssdk.services.protocolquery.ProtocolQueryClient;
import software.amazon.awssdk.services.protocolrestjson.ProtocolRestJsonClient;
import software.amazon.awssdk.services.protocolrestxml.ProtocolRestXmlClient;

@State(Scope.Thread)
@Warmup(iterations = 3, time = 15, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(2) // To reduce difference between each run
@BenchmarkMode(Mode.Throughput)
public class ApiCallProtocolBenchmark {

    private WireMockServer wireMockServer = new WireMockServer(options().port(PORT_NUMBER));

    @Param( {"xml", "json", "ec2", "query"})
    private String protocol;

    private SdkClient client;

    private Runnable runnable;

    @Setup(Level.Trial)
    public void setup() {
        wireMockServer.start();

        configureFor("localhost", PORT_NUMBER);
        URI uri = getUri();

        switch (protocol) {
            case "xml":
                client = ProtocolRestXmlClient.builder()
                                              .endpointOverride(uri)
                                              .build();
                stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(XML_BODY)));
                runnable = () -> ((ProtocolRestXmlClient) client).allTypes(BenchmarkUtils.xmlAllTypeRequest());
                break;
            case "ec2":
                stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(XML_BODY)));
                client = ProtocolEc2Client.builder().endpointOverride(uri).build();
                runnable = () -> ((ProtocolEc2Client) client).allTypes(BenchmarkUtils.ec2AllTypeRequest());
                break;
            case "json":
                client = ProtocolRestJsonClient.builder()
                                               .endpointOverride(uri)
                                               .build();
                stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(JSON_BODY)));
                runnable = () -> ((ProtocolRestJsonClient) client).allTypes(BenchmarkUtils.jsonAllTypeRequest());
                break;
            case "query":
                client = ProtocolQueryClient.builder()
                                            .endpointOverride(uri)
                                            .build();
                stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withBody(XML_BODY)));
                runnable = () -> ((ProtocolQueryClient) client).allTypes(BenchmarkUtils.queryAllTypeRequest());
                break;
            default:
                throw new IllegalArgumentException("invalid protocol");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        wireMockServer.stop();
    }

    @Benchmark
    public void run() {
        runnable.run();
    }

    public static void main(String... args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ApiCallProtocolBenchmark.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();
        new Runner(opt).run();
    }
}
