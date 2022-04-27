package com.braincadet.annotbac;

public class Tools {

    public static String getFileExtension(String filePath) {

        String extension = "";

        int i = filePath.lastIndexOf('.');
        if (i >= 0) {
            extension = filePath.substring(i+1);
        }

        return extension;
    }

    public static String getFileName(String filePath){

        String name = "";

        int i = filePath.lastIndexOf('.');

        System.out.println("i="+i+" | " + filePath);

        if (i >= 0) {
            name = filePath.substring(0, i);
        }

        return name;

    }
}
