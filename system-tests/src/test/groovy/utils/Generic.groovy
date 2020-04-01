package utils

class Generic {
    public static String listToDelimitedString(List array, String delimiter = ",") {
        String str = ""
        array.collect {
            it ->
                if (it == array.last())
                    str += "${it}"
                else
                    str += "${it}${delimiter}"
        }
        return str
    }
}
