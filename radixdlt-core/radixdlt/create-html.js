const showdown  = require('showdown')

converter = new showdown.Converter()
converter.setFlavor('github')

const fs = require('fs')

fs.readFile('src/main/resources/markdown/README.md', 'utf8', function(err, data) {
  // data = data.replace(new RegExp('\\.\/J', 'g'), '\\\./rpc\/J')
  data = data.trim()
  data = data.replace(new RegExp('\\\(\\.', 'g'), '(./documentation')
  data = data.replace(new RegExp('\\.schema\\.md', 'g'), '')
  data = data.replace('## \/', '')

  lines = data.split('\n');

  jsonrpcdata = ''
  otherdata = ''
  for (var i = 0; i < lines.length; i++) {
    data.replace(lines[i], '')
    if (lines[i].startsWith('*') && lines[i].indexOf('JSONRPC') != -1) {
      jsonrpcdata += lines[i] + '\n'
    } else if (lines[i].startsWith('*')) {
      otherdata += lines[i] + '\n'
    }
  }

  data = '# Radix JSON RPC API\n' + jsonrpcdata + '\n# Other\n' + otherdata

  var page = '<!DOCTYPE html><head><link rel="stylesheet" href="http://localhost:8080/documentation/github.min.css"><link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css"></head><body class="markdown-body container">'
  page += converter.makeHtml(data).trim() + '</body>'
  fs.writeFile('src/main/resources/documentation/index.html', page, function(err) {
    if (err) throw err;
  })
})

fs.readdir('src/main/resources/markdown', function(err, items) {
  items = items.filter(item => item.indexOf('.md') != -1 && item.indexOf('README') == -1)
  for (let i = 0; i < items.length; i++) {
    fs.readFile('src/main/resources/markdown/' + items[i], 'utf8', function(err, data) {
      data = data.replace(new RegExp('\\.schema\\.md', 'g'), '')
      let documentationPage = '<!DOCTYPE html><head><link rel="stylesheet" href="http://localhost:8080/documentation/github.min.css"><link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css"></head><body class="markdown-body container">'
      documentationPage += converter.makeHtml(data).trim() + '</body>'
      fs.writeFile('src/main/resources/documentation/' + items[i].replace('.schema.md', '.html'), documentationPage, function(err) {
        if (err) throw err;
      })
    })
  }
})


