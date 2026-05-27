package com.bbsod.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.bridge.SLF4JBridgeHandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MyServlet extends HttpServlet {

    private static final String OTEL_ATTRIBUTE_SERVICE_NAME = "service.name";

    private static final String SERVICE_NAME = "ht-tomcat-service";

    private static final String OTLP_GRPC_EXPORTER_ENDPOINT = "http://ht-otel-collector:4317";

    private static final Long OTEL_METER_INTERVAL_SECONDS = 10L;

    private static final String INSTRUMENTATION_SCOPE_NAME = MyServlet.class.getName();
    private static final String METRIC_NAME = "app.db.db_requests"; // Look the semantic convention to avoid conflict.
    private static final String METRIC_DESCRIPTION = "Count DB requests";

    private final Meter otelMeter;
    private final LongCounter requestCounter;
    private final Tracer otelTracer;
    private static final java.util.logging.Logger julLogger = Logger.getLogger("jul-logger") ;

    private Context otelParentContext;

    // Constructor
    public MyServlet() {

        OpenTelemetry otel = initOpenTelemetry();

        this.otelMeter = otel.getMeter(INSTRUMENTATION_SCOPE_NAME);

        LongCounter counterMetric = this.otelMeter.counterBuilder(METRIC_NAME)
                .setDescription(METRIC_DESCRIPTION)
                .build();

        this.requestCounter = counterMetric;

        this.otelTracer = otel.getTracer(INSTRUMENTATION_SCOPE_NAME);

        // Install OpenTelemetry in logback appender
        OpenTelemetryAppender.install(otel);

        // Route JUL logs to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

    }

    private static OpenTelemetry initOpenTelemetry() {

        // Resource service.name setup
        Attributes otelAttributes = Attributes.of(
                AttributeKey.stringKey(OTEL_ATTRIBUTE_SERVICE_NAME), SERVICE_NAME);

        Resource otelResource = Resource.create(otelAttributes);

        /* CUSTOM METRIC SETUP */

        // OTLP Exporter setup
        OtlpGrpcMetricExporter otlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(OTLP_GRPC_EXPORTER_ENDPOINT)
                .build();

        // Metric reader setup
        PeriodicMetricReader otelPeriodicMetricReader = PeriodicMetricReader.builder(otlpGrpcMetricExporter)
                .setInterval(Duration.ofSeconds(OTEL_METER_INTERVAL_SECONDS))
                .build();

        // Otel Meter Provider setup
        SdkMeterProvider otelSdkMeterProvider = SdkMeterProvider.builder()
                .addResource(otelResource)
                .registerMetricReader(otelPeriodicMetricReader)
                .build();

        /* CUSTOM SPAN SETUP */

        OtlpGrpcSpanExporter otlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(OTLP_GRPC_EXPORTER_ENDPOINT)
                .build();

        SimpleSpanProcessor otelSimpleSpanProcessor = SimpleSpanProcessor.builder(otlpGrpcSpanExporter).build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addResource(otelResource)
                .addSpanProcessor(otelSimpleSpanProcessor)
                .build();

        /* LOGs SETUP */
        OtlpGrpcLogRecordExporter otlpGrpcLogRecordExporter = OtlpGrpcLogRecordExporter.builder()
                .setEndpoint(OTLP_GRPC_EXPORTER_ENDPOINT)
                .build();

        BatchLogRecordProcessor otelBatchLogRecordProcessor = BatchLogRecordProcessor.builder(otlpGrpcLogRecordExporter)
                .build();

        SdkLoggerProvider otelSdkLoggerProvider = SdkLoggerProvider.builder().setResource(otelResource)
                .addLogRecordProcessor(otelBatchLogRecordProcessor).build();

        // Otel SDK setup.
        OpenTelemetrySdk otelSdk = OpenTelemetrySdk.builder()
                .setMeterProvider(otelSdkMeterProvider)
                .setTracerProvider(sdkTracerProvider)
                .setLoggerProvider(otelSdkLoggerProvider)
                .build();

        // Cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(otelSdk::close));

        return otelSdk; // OpenTelemetrySdk implements OpenTelemetry interface.
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        List<JSONObject> dataList = new ArrayList<>();
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        // Parent span for context propagation setup
        Span otelDoGetSpan = this.otelTracer.spanBuilder("Do Get")
                .setNoParent()
                .startSpan();

        try {
            otelDoGetSpan.makeCurrent();

            this.otelParentContext = Context.current().with(otelDoGetSpan);

            // Sleep for 2 seconds
            sleepFor(2000);

            // Establish database connection and get data
            this.requestCounter.add(1);

            out.println("<html><body>");
            out.println("<h1>Database Results</h1>");

            getDatabaseResults(dataList, out);

            // Make a request to the Python microservice
            String averageAge = getAverageAge(dataList);

            out.println("<h2>Average Age: " + averageAge + "</h2>");
            out.println("</body></html>");

        } finally {
            otelDoGetSpan.end();
        }

    }

    private void sleepFor(long millis) {
        Span otelSleepSpan = this.otelTracer.spanBuilder("Sleep for two seconds")
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(this.otelParentContext)
                .startSpan();

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            otelSleepSpan.end();
        }
    }

    private void getDatabaseResults(List<JSONObject> dataList, PrintWriter out) {
        Span otelDbSpan = this.otelTracer.spanBuilder("Database Connection")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(this.otelParentContext)
                .startSpan();

        // JDBC connection parameters
        String jdbcUrl = "jdbc:mysql://ht-mysql:3306/mydatabase";
        String jdbcUser = "myuser";
        String jdbcPassword = "mypassword";

        try {
            julLogger.info("Database connection initiated");

            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser,
                    jdbcPassword);

            // Create a statement
            Statement statement = connection.createStatement();

            // Execute a query
            String query = "SELECT * FROM mytable";
            ResultSet resultSet = statement.executeQuery(query);

            // Build web page
            out.println("<table border='1'>");
            out.println("<tr><th>ID</th><th>Name</th><th>Age</th></tr>");

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                int age = resultSet.getInt("age");
                out.println("<tr><td>" + id + "</td><td>" + name + "</td><td>" + age
                        + "</td></tr>");

                JSONObject dataObject = new JSONObject();
                dataObject.put("id", id);
                dataObject.put("name", name);
                dataObject.put("age", age);
                dataList.add(dataObject);

            }
            out.println("</table>");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC Driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Connection failed.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("<h2>Error: " + e.getMessage() + "</h2>");
        } finally {
            otelDbSpan.end();
        }
    }

    private String getAverageAge(List<JSONObject> dataList) throws IOException {

        Span otelAverageSpan = this.otelTracer.spanBuilder("Compute Average Age")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(otelParentContext)
                .startSpan();

        Context otelAverageContext = Context.current().with(otelAverageSpan);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://ht-python-service:5000/compute_average_age");
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject requestData = new JSONObject();
            requestData.put("data", new JSONArray(dataList));

            StringEntity entity = new StringEntity(requestData.toString());
            httpPost.setEntity(entity);

            // W3CTraceContext for span context propagation
            W3CTraceContextPropagator traceContextPropagator = W3CTraceContextPropagator.getInstance();
            traceContextPropagator.inject(otelAverageContext, httpPost, HttpPost::setHeader);

            String responseString = httpClient.execute(httpPost,
                    response -> EntityUtils.toString(response.getEntity()));
            JSONObject responseJson = new JSONObject(responseString);
            return responseJson.get("average_age").toString();
        } finally {
            otelAverageSpan.end();
        }
    }
}