/*
 * The app depends on com.tom-roush:pdfbox-android, an Android .aar repackaging
 * of Apache PDFBox 2.x under the com.tom_roush namespace. On the JVM the same
 * API ships as org.apache.pdfbox, so these aliases let the real BookBuilder /
 * RagExporter sources compile and run unmodified off-device.
 */
package com.tom_roush.pdfbox.pdmodel.graphics.color

typealias PDColor = org.apache.pdfbox.pdmodel.graphics.color.PDColor
typealias PDDeviceRGB = org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
