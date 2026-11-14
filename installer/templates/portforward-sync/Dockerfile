FROM alpine:3.21
RUN apk add --no-cache curl
COPY sync.sh /sync.sh
ENTRYPOINT ["/bin/sh", "/sync.sh"]
