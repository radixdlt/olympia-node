var _getIterator = require("../core-js/get-iterator");

var _isIterable = require("../core-js/is-iterable");

function _slicedToArrayLoose(arr, i) {
  if (Array.isArray(arr)) {
    return arr;
  } else if (_isIterable(Object(arr))) {
    var _arr = [];

    for (var _iterator = _getIterator(arr), _step; !(_step = _iterator.next()).done;) {
      _arr.push(_step.value);

      if (i && _arr.length === i) break;
    }

    return _arr;
  } else {
    throw new TypeError("Invalid attempt to destructure non-iterable instance");
  }
}

module.exports = _slicedToArrayLoose;