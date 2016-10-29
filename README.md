# drones-sfm

Docker container to pull images from S3, run OpenSfM over them and push the results back to S3.

## Building

```
docker build -t drones-sfm .
```

## Running

```
docker run --rm -t \
           -e SRC_BUCKET=<bucket-name> \
           -e DST_BUCKET=<bucket-name> \
           -e FOLDER=<folder-name> \
           drones-sfm
```
