'use strict';

var benchmark = require('vdom-benchmark-base');
var BenchmarkImpl = require('./compiled/vdom-benchmark-domaren');

var NAME = 'domaren';
var VERSION = '0.1.0';

document.addEventListener('DOMContentLoaded', function(e) {
  benchmark(NAME, VERSION, BenchmarkImpl);
}, false);
