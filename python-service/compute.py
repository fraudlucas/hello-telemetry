from flask import Flask, request, jsonify

from opentelemetry import metrics, trace
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry._logs import set_logger_provider

import logging

APP_SERVICE_NAME = "ht-python-service"
OTEL_ATTRIBUTE_SERVICE_NAME = "service.name"
OTLP_GRPC_EXPORTER_ENDPOINT = "http://ht-otel-collector:4317"
OTEL_METER_INTERVAL_SECONDS = 10000
OTEL_INSTRUMENTATION_SCOPE_NAME = __name__
OTEL_CUSTOM_METRIC_NAME = "app_compute_request_count"
OTEL_CUSTOM_METRIC_DESCRIPTION = "Counts the requests to compute-service"


otel_resource = Resource.create({OTEL_ATTRIBUTE_SERVICE_NAME: APP_SERVICE_NAME})

# Metrics setup: exporter, metric reader, meter provider

otlp_metric_exporter = OTLPMetricExporter(
    endpoint=OTLP_GRPC_EXPORTER_ENDPOINT, insecure=True
)

otel_metric_reader = PeriodicExportingMetricReader(
    exporter=otlp_metric_exporter, export_interval_millis=OTEL_METER_INTERVAL_SECONDS
)

otel_meter_provider = MeterProvider(
    resource=otel_resource, metric_readers=(otel_metric_reader,)
)

metrics.set_meter_provider(otel_meter_provider)

otel_meter = metrics.get_meter(OTEL_INSTRUMENTATION_SCOPE_NAME)

otel_compute_request_count = otel_meter.create_counter(
    name=OTEL_CUSTOM_METRIC_NAME,
    description=OTEL_CUSTOM_METRIC_DESCRIPTION,
)

# Traces setup: span exporter, span processor, tracer provider

otlp_span_exporter = OTLPSpanExporter(
    endpoint=OTLP_GRPC_EXPORTER_ENDPOINT, insecure=True
)

otel_span_processor = BatchSpanProcessor(otlp_span_exporter)

otel_tracer_provider = TracerProvider(resource=otel_resource)
otel_tracer_provider.add_span_processor(otel_span_processor)

trace.set_tracer_provider(otel_tracer_provider)

otel_tracer = trace.get_tracer(OTEL_INSTRUMENTATION_SCOPE_NAME)

# Logs setup: exporter, processor, provider, handler

otlp_logs_exporter = OTLPLogExporter(
    endpoint=OTLP_GRPC_EXPORTER_ENDPOINT, insecure=True
)

otel_logs_processor = BatchLogRecordProcessor(exporter=otlp_logs_exporter)

otel_logs_provider = LoggerProvider(resource=otel_resource)
otel_logs_provider.add_log_record_processor(otel_logs_processor)

set_logger_provider(otel_logs_provider)

otel_logs_handler = LoggingHandler(
    level=logging.NOTSET, logger_provider=otel_logs_provider
)

# Logging and logger setup

logging.basicConfig(level=logging.NOTSET, handlers=[otel_logs_handler])

otel_logger = logging.getLogger()


app = Flask(__name__)


@app.route("/compute_average_age", methods=["POST"])
def compute_average_age():

    # Extract Otel trace context
    otel_trace_context = TraceContextTextMapPropagator().extract(request.headers)

    # Starting a new span
    with otel_tracer.start_as_current_span(
        name="Compute Average Span", context=otel_trace_context
    ):

        otel_logger.info("Average age compute in progress")

        # Increment compute_request_count
        otel_compute_request_count.add(1)

        # Process the request data
        data = request.json["data"]
        if not data:
            return jsonify({"error": "No data provided"}), 400

        # Extract ages from the data
        ages = [item["age"] for item in data if "age" in item]
        if not ages:
            return jsonify({"error": "No age data available"}), 400

        # Compute the average age
        average_age = round(sum(ages) / len(ages), 1)

        return jsonify({"average_age": average_age})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
