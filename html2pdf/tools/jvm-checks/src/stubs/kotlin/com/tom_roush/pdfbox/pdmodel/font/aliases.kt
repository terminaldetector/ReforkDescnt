/*
 * The app depends on com.tom-roush:pdfbox-android, an Android .aar repackaging
 * of Apache PDFBox 2.x under the com.tom_roush namespace. On the JVM the same
 * API ships as org.apache.pdfbox, so these aliases let the real BookBuilder /
 * RagExporter sources compile and run unmodified off-device.
 */
package com.tom_roush.pdfbox.pdmodel.font

typealias PDFont = org.apache.pdfbox.pdmodel.font.PDFont
typealias PDType0Font = org.apache.pdfbox.pdmodel.font.PDType0Font
typealias PDType1Font = org.apache.pdfbox.pdmodel.font.PDType1Font
