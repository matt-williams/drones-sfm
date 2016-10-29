'use strict';

var config = require('./config.json');
var express = require('express');
var morgan = require('morgan');
var aws = require('aws-sdk');
var Docker = require('dockerode');

var s3img = new aws.S3({params: {Bucket: config.S3_BUCKET_IMG}});
var s3data = new aws.S3({params: {Bucket: config.S3_BUCKET_DATA}});
var docker = new Docker();

function process(res, folder) {
  docker.listContainers(function (err, containers) {
    containers.forEach(function (containerInfo) {
console.log(containerInfo);
//      docker.getContainer(containerInfo.Id).stop(cb);
    });
  });
  docker.createContainer({AutoRemove: true,
                          Env: ['SRC_BUCKET=' + config.S3_BUCKET_IMG,
                                'DST_BUCKET=' + config.S3_BUCKET_DATA,
                                'FOLDER=' + folder],
                          Image: config.DOCKER_IMG,
                          Name: 'drones-sfm-' + folder,
                          Tty: true},
    function (err, container) {
      if (err) {
        res.status(500).send(err);
      } else {
        container.start(function (err, data) {
          if (err) {
            res.status(500).send(err);
          } else {
            container.attach({stream: true, stdout: true, stderr: true}, function (err, stream) {
              if (err) {
                res.status(500).send(err);
              } else {
                stream.pipe(res);
              }
            });
          }
        });
      }
    });
};

var app = express();
app.use(morgan("combined"));
app.use('/api/:folder', express.static('static/api/folder'));
app.put('/api/:folder/images/:file', function(req, res) {
  var folder = req.params.folder;
  var file = req.params.file;
  s3img.upload({Key: folder + '/' + file,
                ContentType: req.get('Content-Type'),
                Body: req},
    function(err, data) {
      if (err) {
        res.status(err.code).send(err);
      } else if (req.query.process !== undefined) {
        process(res, folder);
      } else {
        res.status(200).send();
      }
    });
});
app.get('/api/:folder/images/:file', function(req, res) {
  var folder = req.params.folder;
  var file = req.params.file;
  s3img.headObject({Key: folder + '/' + file}, function(err, data) {
     if (err) {
       res.status(err.code).send(err);
     } else {
       res.writeHead(200, {
         "Content-Length": data.ContentLength,
         "Content-Type": data.ContentType
       });
       s3img.getObject({Key: folder + '/' + file}).createReadStream().pipe(res);
     }
   });
});
app.post('/api/:folder/process', function(req, res) {
  var folder = req.params.folder;
  process(res, folder);
});
app.use('/api/:folder', function(req, res) {
  var folder = req.params.folder;
  var file = req.path.substring(1);
  s3data.headObject({Key: folder + '/' + file}, function(err, data) {
     if (err) {
       res.status(err.code).send(err);
     } else {
       res.writeHead(200, {
         "Content-Length": data.ContentLength,
         "Content-Type": data.ContentType
       });
       s3data.getObject({Key: folder + '/' + file}).createReadStream().pipe(res);
     }
   });
});

app.use('/', express.static('static'));
app.listen(config.HTTP_PORT, function() {
  console.log('HTTP server listening on ' + config.HTTP_PORT);
});
