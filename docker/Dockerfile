FROM freakthemighty/opensfm

RUN rm -rf /source/OpenSfM/data 
RUN pip install boto3
RUN pip install https://storage.googleapis.com/tensorflow/linux/cpu/tensorflow-0.10.0-cp27-none-linux_x86_64.whl
RUN python /usr/local/lib/python2.7/dist-packages/tensorflow/models/image/imagenet/classify_image.py
COPY . /source/drones

ENTRYPOINT ["/source/drones/run.py"]
