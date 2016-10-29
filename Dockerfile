FROM freakthemighty/opensfm

RUN rm -rf /source/OpenSfM/data 
RUN pip install boto3
COPY . /source/drones

ENTRYPOINT ["/source/drones/run.py"]
