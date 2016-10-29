#!/usr/bin/python

import os
import boto3

src_bucket = os.environ['SRC_BUCKET']
dst_bucket = os.environ['DST_BUCKET']
folder = os.environ['FOLDER']
data_dir = '/source/drones/data';

s3c = boto3.client('s3')
for entry in s3c.list_objects(Bucket=src_bucket, Prefix=folder + '/')['Contents']:
    key = entry['Key']
    if key != folder + '/':
        print 'Downloading %s' % (key,)
        s3c.download_file(src_bucket, key, key.replace(folder + '/', data_dir + '/images/'))

os.system('/source/OpenSfM/bin/run_all %s' % (data_dir,))

for root, dirnames, filenames in os.walk(data_dir):
    if not root.startswith(data_dir + '/images'):
        for filename in filenames:
            file = root + '/' + filename
            print 'Uploading %s' % (file,)
            s3c.upload_file(file, dst_bucket, file.replace(data_dir + '/', folder + '/'))
