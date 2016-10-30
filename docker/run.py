#!/usr/bin/python

import os
import Image
import boto3
import subprocess
import time

img_bucket = os.environ['IMG_BUCKET']
img_320_bucket = os.environ['IMG_320_BUCKET']
data_bucket = os.environ['DATA_BUCKET']
table = os.environ['TABLE']
folder = os.environ['FOLDER']
data_dir = '/source/drones/data';

s3c = boto3.client('s3')
dynamo = boto3.client('dynamodb', region_name="us-east-1")

for entry in s3c.list_objects(Bucket=img_bucket, Prefix=folder + '/')['Contents']:
    key = entry['Key']
    if key != folder + '/':
        print 'Downloading %s to %s' % (key, file)
	file = key.replace(folder + '/', data_dir + '/images/')
        s3c.download_file(img_bucket, key, file)
        try:
            file320 = file.replace("/images/", "/images320/")
            print 'Downscaling %s to %s' % (file, file320)
            im = Image.open(file)
            im.thumbnail((320, int((float(im.size[1]) * 320 / float(im.size[0])))), Image.ANTIALIAS)
            im.save(file320)
        except:
            print 'Failed to convert image %s' % (file,)

os.system('/source/OpenSfM/bin/run_all %s' % (data_dir,))

thumbnail = ""
for root, dirnames, filenames in os.walk(data_dir + '/images320'):
    for filename in filenames:
        if thumbnail == '' and filename != '.gitignore':
            thumbnail = filename
        file = root + '/' + filename
        key = file.replace(data_dir + '/images320/', folder + '/')
        print 'Uploading %s to %s' % (file, key)
        s3c.upload_file(file, img_320_bucket, key)

for root, dirnames, filenames in os.walk(data_dir):
    if not root.startswith(data_dir + '/images'):
        for filename in filenames:
            file = root + '/' + filename
            key = file.replace(data_dir + '/', folder + '/')
            print 'Uploading %s to %s' % (file, key)
            s3c.upload_file(file, data_bucket, key)

import tensorflow
print 'Tensorflow-ing %s' % (data_dir + '/images/' + thumbnail,)
taglines = subprocess.check_output(["python", "/usr/local/lib/python2.7/dist-packages/tensorflow/models/image/imagenet/classify_image.py", "--image_file", data_dir + '/images/' + thumbnail]).decode()
tags = [tagline.split('(')[0].split(',')[0].strip() for tagline in taglines.split('\n') if tagline.split('(')[0].split(',')[0]]

print 'Writing %s to dynamodb' % (folder,)
dynamo.put_item(TableName=table,
                Item={'folder': {'S': folder},
                      'thumbnail': {'S': thumbnail},
                      'tags': {'SS': tags},
                      'timestamp': {'N': str(int(time.time()))}});
