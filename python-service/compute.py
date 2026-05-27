from flask import Flask, request, jsonify

from opentelemetry import metrics, trace
from opentelemetry.sdk.resources import Resource

import logging

APP_SERVICE_NAME = "ht-python-service"
OTEL_ATTRIBUTE_SERVICE_NAME = "service.name"
OTEL_INSTRUMENTATION_SCOPE_NAME = __name__
OTEL_CUSTOM_METRIC_NAME = "app_compute_request_count"
OTEL_CUSTOM_METRIC_DESCRIPTION = "Counts the requests to compute-service"


otel_resource = Resource.create({OTEL_ATTRIBUTE_SERVICE_NAME: APP_SERVICE_NAME})

# Metrics setup:

otel_meter = metrics.get_meter(OTEL_INSTRUMENTATION_SCOPE_NAME)

otel_compute_request_count = otel_meter.create_counter(
    name=OTEL_CUSTOM_METRIC_NAME,
    description=OTEL_CUSTOM_METRIC_DESCRIPTION,
)

# Tracer setup:

otel_tracer = trace.get_tracer(OTEL_INSTRUMENTATION_SCOPE_NAME)

# Logs setup:

otel_logger = logging.getLogger(OTEL_INSTRUMENTATION_SCOPE_NAME)
otel_logger.setLevel(logging.INFO)


app = Flask(__name__)


@app.route("/compute_average_age", methods=["POST"])
def compute_average_age():

    # Starting a new span
    with otel_tracer.start_as_current_span(name="Compute Average Span"):

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
