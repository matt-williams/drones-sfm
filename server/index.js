'use strict';

var config = require('./config.json');
var express = require('express');
var morgan = require('morgan');
var aws = require('aws-sdk');
var Docker = require('dockerode');

var dynamo = new aws.DynamoDB({params: {TableName: config.DYNAMO_TABLE}, region: 'us-east-1'});
var s3img = new aws.S3({params: {Bucket: config.S3_BUCKET_IMG}});
var s3img320 = new aws.S3({params: {Bucket: config.S3_BUCKET_IMG_320}});
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
                          Env: ['IMG_BUCKET=' + config.S3_BUCKET_IMG,
                                'IMG_320_BUCKET=' + config.S3_BUCKET_IMG_320,
                                'DATA_BUCKET=' + config.S3_BUCKET_DATA,
                                'TABLE=' + config.DYNAMO_TABLE,
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
                stream.on('data', function(chunk) {
                  console.log(chunk.toString().replace(/\n$/, ''));
                  res.write(chunk);
                });
                stream.on('end', function() {
                  res.end();
                });
              }
            });
          }
        });
      }
    });
};

var app = express();
app.use(morgan("combined"));
app.get('/api/', function(req, res) {
  dynamo.scan({}, function(err, data) {
    if (err) {
      res.status(err.code).send(err);
    } else {
      var items = data.Items;
      var array = [];
      for (var ii = 0; ii < items.length; ii++) {
        array.push({folder: items[ii].folder.S, thumbnail: items[ii].thumbnail.S});
      }
      res.send(array);
    }
  });
});
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
app.get('/api/:folder/images320/:file', function(req, res) {
  var folder = req.params.folder;
  var file = req.params.file;
  s3img320.headObject({Key: folder + '/' + file}, function(err, data) {
     if (err) {
       res.status(err.code).send(err);
     } else {
       res.writeHead(200, {
         "Content-Length": data.ContentLength,
         "Content-Type": data.ContentType
       });
       s3img320.getObject({Key: folder + '/' + file}).createReadStream().pipe(res);
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
console.log(folder + '/' + file);
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

app.use('/js/jquery', express.static('node_modules/jquery/dist'));
app.use('/js/vue', express.static('node_modules/vue/dist'));
app.use('/', express.static('static'));
app.listen(config.HTTP_PORT, function() {
  console.log('HTTP server listening on ' + config.HTTP_PORT);
});
