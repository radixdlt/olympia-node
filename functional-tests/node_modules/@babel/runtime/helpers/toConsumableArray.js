var _Array$from = require("../core-js/array/from");

function _toConsumableArray(arr) {
  if (Array.isArray(arr)) {
    for (var i = 0, arr2 = new Array(arr.length); i < arr.length; i++) {
      arr2[i] = arr[i];
    }

    return arr2;
  } else {
    return _Array$from(arr);
  }
}

module.exports = _toConsumableArray;