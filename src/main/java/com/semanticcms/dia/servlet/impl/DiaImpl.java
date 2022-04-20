/*
 * semanticcms-dia-servlet - Java API for embedding Dia-based diagrams in web pages in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-dia-servlet.
 *
 * semanticcms-dia-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-dia-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-dia-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.dia.servlet.impl;

import com.aoapps.concurrent.KeyedConcurrencyReducer;
import com.aoapps.encoding.JavaScriptWriter;
import com.aoapps.hodgepodge.awt.image.ImageSizeCache;
import com.aoapps.html.any.AnyA;
import com.aoapps.html.any.AnyIMG;
import com.aoapps.html.any.AnyPhrasingContent;
import com.aoapps.html.any.AnySCRIPT;
import com.aoapps.lang.ProcessResult;
import com.aoapps.lang.concurrent.ExecutionExceptions;
import com.aoapps.lang.exception.WrappedException;
import com.aoapps.lang.util.Sequence;
import com.aoapps.lang.util.UnsynchronizedSequence;
import com.aoapps.net.URIEncoder;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.servlet.lastmodified.LastModifiedServlet;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.ConcurrencyCoordinator;
import com.semanticcms.core.servlet.PageIndex;
import com.semanticcms.core.servlet.PageRefResolver;
import com.semanticcms.dia.model.Dia;
import com.semanticcms.dia.servlet.DiaExportServlet;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class DiaImpl {

  /** Make no instances. */
  private DiaImpl() {
    throw new AssertionError();
  }

  private static final String LINUX_DIA_PATH = "/usr/bin/dia";

  private static final String WINDOWS_DIA_PATH = "C:\\Program Files (x86)\\Dia\\bin\\dia.exe";

  // This was used for opening the diagram, moved to semanticcms-openfile-servlet to avoid dependency.
  //private static final String WINDOWS_DIAW_PATH = "C:\\Program Files (x86)\\Dia\\bin\\diaw.exe";

  private static class TempDirLock {/* Empty lock class to help heap profile */}
  private static final TempDirLock tempDirLock = new TempDirLock();
  private static final String TEMP_SUBDIR = DiaExport.class.getName();

  private static final String MISSING_IMAGE_PATH = "/semanticcms-dia-servlet/images/broken-chain-1164481-640x480.jpg";
  private static final int MISSING_IMAGE_WIDTH = 640;
  private static final int MISSING_IMAGE_HEIGHT = 480;

  public static final char SIZE_SEPARATOR = '-';
  public static final char EMPTY_SIZE = '_';
  public static final char DIMENSION_SEPARATOR = 'x';
  public static final String PNG_EXTENSION = ".png";

  /**
   * The request key used to ensure per-request unique element IDs.
   */
  private static final ScopeEE.Request.Attribute<Sequence> ID_SEQUENCE_REQUEST_ATTRIBUTE =
    ScopeEE.REQUEST.attribute(DiaImpl.class.getName() + ".idSequence");

  /**
   * The alt link ID prefix.
   */
  private static final String ALT_LINK_ID_PREFIX = "semanticcms-dia-servlet-alt-pixel-ratio-";

  /**
   * The default width when neither width nor height provided.
   */
  private static final int DEFAULT_WIDTH = 200;

  /**
   * The supported pixel densities, these must be ordered from lowest to highest.  The first is the default.
   */
  private static final int[] PIXEL_DENSITIES = {
    1,
    2,
    3,
    4
  };

  private static boolean isWindows() {
    String osName = System.getProperty("os.name");
    return osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
  }

  private static String getDiaExportPath() {
    if (isWindows()) {
      return WINDOWS_DIA_PATH;
    } else {
      return LINUX_DIA_PATH;
    }
  }

  /* This was used for opening the diagram, moved to semanticcms-openfile-servlet to avoid dependency.
  public static String getDiaOpenPath() {
    if (isWindows()) {
      return WINDOWS_DIAW_PATH;
    } else {
      return LINUX_DIA_PATH;
    }
  }
   */

  /**
   * Make sure each diagram and scaling is only exported once when under concurrent access.
   */
  private static final KeyedConcurrencyReducer<File, Void> exportConcurrencyLimiter = new KeyedConcurrencyReducer<>();

  public static DiaExport exportDiagram(
    PageRef pageRef,
    final Integer width,
    final Integer height,
    File tmpDir
  ) throws InterruptedException, FileNotFoundException, IOException {
    final File diaFile = pageRef.getResourceFile(true, true);

    String diaPath = pageRef.getPath();
    // Strip extension if matches expected value
    if (diaPath.toLowerCase(Locale.ROOT).endsWith(Dia.DOT_EXTENSION)) {
      diaPath = diaPath.substring(0, diaPath.length() - Dia.DOT_EXTENSION.length());
    }
    // Generate the temp filename
    final File tmpFile;
    synchronized (tempDirLock) {
      tmpFile = new File(
        tmpDir,
        TEMP_SUBDIR
          + pageRef.getBookPrefix().replace('/', File.separatorChar)
          + diaPath.replace('/', File.separatorChar)
          + "-"
          + (width == null ? "_" : width.toString())
          + "x"
          + (height == null ? "_" : height.toString())
          + PNG_EXTENSION
      );
      // Make temp directory if needed (and all parents)
      tmpDir = tmpFile.getParentFile();
      if (!tmpDir.exists()) {
        Files.createDirectories(tmpDir.toPath());
      }
    }
    // Re-export when missing or timestamps indicate needs recreated
    try {
      exportConcurrencyLimiter.executeSerialized(
        tmpFile,
        () -> {
          if (!tmpFile.exists() || diaFile.lastModified() >= tmpFile.lastModified()) {
            // Determine size for scaling
            final String sizeParam;
            if (width == null) {
              if (height == null) {
                sizeParam = null;
              } else {
                sizeParam = "x" + height;
              }
            } else {
              if (height == null) {
                sizeParam = width + "x";
              } else {
                sizeParam = width + "x" + height;
              }
            }
            // Build the command
            final String diaExePath = getDiaExportPath();
            final String[] command;
            if (sizeParam == null) {
              command = new String[] {
                diaExePath,
                "--export=" + tmpFile.getCanonicalPath(),
                "--filter=png",
                "--log-to-stderr",
                diaFile.getCanonicalPath()
              };
            } else {
              command = new String[] {
                diaExePath,
                "--export=" + tmpFile.getCanonicalPath(),
                "--filter=png",
                "--size=" + sizeParam,
                "--log-to-stderr",
                diaFile.getCanonicalPath()
              };
            }
            // Export using dia
            ProcessResult result = ProcessResult.exec(command);
            int exitVal = result.getExitVal();
            if (exitVal != 0) {
              throw new IOException(diaExePath + ": non-zero exit value: " + exitVal);
            }
            if (!isWindows()) {
              // Dia does not set non-zero exit value, instead, it writes both errors and normal output to stderr
              // (Dia version 0.97.2, compiled 23:51:04 Apr 13 2012)
              String normalOutput = diaFile.getCanonicalPath() + " --> " + tmpFile.getCanonicalPath();
              // Read the standard error, if any one line matches the expected line, then it is OK
              // other lines include stuff like: Xlib:  extension "RANDR" missing on display ":0".
              boolean foundNormalOutput = false;
              String stderr = result.getStderr();
              try (BufferedReader errIn = new BufferedReader(new StringReader(stderr))) {
                String line;
                while ((line = errIn.readLine()) != null) {
                  if (line.equals(normalOutput)) {
                    foundNormalOutput = true;
                    break;
                  }
                }
              }
              if (!foundNormalOutput) {
                throw new IOException(diaExePath + ": " + stderr);
              }
            }
          }
          return null;
        }
      );
    } catch (ExecutionException e) {
      // Maintain expected exception types while not losing stack trace
      ExecutionExceptions.wrapAndThrowWithTemplate(e, FileNotFoundException.class, (template, cause) -> {
        FileNotFoundException fnf = new FileNotFoundException(template.getMessage());
        fnf.initCause(cause);
        return fnf;
      });
      ExecutionExceptions.wrapAndThrow(e, IOException.class, IOException::new);
      throw new WrappedException(e);
    }
    // Get actual dimensions
    Dimension pngSize = ImageSizeCache.getImageSize(tmpFile);

    return new DiaExport(
      tmpFile,
      pngSize.width,
      pngSize.height
    );
  }

  private static String buildUrlPath(
    HttpServletRequest request,
    PageRef pageRef,
    int width,
    int height,
    int pixelDensity,
    DiaExport export
  ) throws ServletException {
    String diaPath = pageRef.getPath();
    // Strip extension
    if (!diaPath.endsWith(Dia.DOT_EXTENSION)) {
      throw new ServletException("Unexpected file extension for diagram: " + diaPath);
    }
    diaPath = diaPath.substring(0, diaPath.length() - Dia.DOT_EXTENSION.length());
    StringBuilder urlPath = new StringBuilder();
    urlPath
      .append(request.getContextPath())
      .append(DiaExportServlet.SERVLET_PATH)
      .append(pageRef.getBookPrefix())
      .append(diaPath)
      .append(SIZE_SEPARATOR);
    if (width == 0) {
      urlPath.append(EMPTY_SIZE);
    } else {
      urlPath.append(width * pixelDensity);
    }
    urlPath.append(DIMENSION_SEPARATOR);
    if (height == 0) {
      urlPath.append(EMPTY_SIZE);
    } else {
      urlPath.append(height * pixelDensity);
    }
    urlPath.append(PNG_EXTENSION);
    // Check for header disabling auto last modified
    if (!"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))) {
      urlPath
        .append('?')
        .append(LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME)
        .append('=')
        .append(LastModifiedServlet.encodeLastModified(export.getTmpFile().lastModified()))
      ;
    }
    return urlPath.toString();
  }

  /**
   * @param  content  {@link AnyPhrasingContent} provides {@link AnyIMG}, {@link AnyA}, and {@link AnySCRIPT}.
   */
  public static void writeDiaImpl(
    ServletContext servletContext,
    HttpServletRequest request,
    HttpServletResponse response,
    AnyPhrasingContent<?, ?> content,
    Dia dia
  ) throws ServletException, IOException {
    try {
      // Get the current capture state
      final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
      if (captureLevel.compareTo(CaptureLevel.META) >= 0) {
        final PageRef pageRef = PageRefResolver.getPageRef(servletContext, request, dia.getBook(), dia.getPath());
        if (captureLevel == CaptureLevel.BODY) {
          // Use default width when neither provided
          int width = dia.getWidth();
          int height = dia.getHeight();
          if (width == 0 && height == 0) {
            width = DEFAULT_WIDTH;
          }
          File resourceFile = pageRef.getResourceFile(false, true);
          // Scale concurrently for each pixel density
          List<DiaExport> exports;
          if (resourceFile == null) {
            exports = null;
          } else {
            final File tempDir = ScopeEE.Application.TEMPDIR.context(servletContext).get();
            final int finalWidth = width;
            final int finalHeight = height;
            // TODO: Avoid concurrent tasks when all diagrams are already up-to-date?
            // TODO: Fetch resource file once when first needed?
            List<Callable<DiaExport>> tasks = new ArrayList<>(PIXEL_DENSITIES.length);
            for (int i=0; i<PIXEL_DENSITIES.length; i++) {
              final int pixelDensity = PIXEL_DENSITIES[i];
              tasks.add(
                () -> exportDiagram(
                  pageRef,
                  finalWidth == 0 ? null : (finalWidth * pixelDensity),
                  finalHeight == 0 ? null : (finalHeight * pixelDensity),
                  tempDir
                )
              );
            }
            try {
              exports = ConcurrencyCoordinator.getRecommendedExecutor(servletContext, request).callAll(tasks);
            } catch (ExecutionException e) {
              // Maintain expected exception types while not losing stack trace
              ExecutionExceptions.wrapAndThrow(e, IOException.class, IOException::new);
              throw new ServletException(e);
            }
          }
          // Get the thumbnail image in default pixel density
          DiaExport export = exports == null ? null : exports.get(0);
          // Find id sequence
          Sequence idSequence = ID_SEQUENCE_REQUEST_ATTRIBUTE.context(request)
            .computeIfAbsent(__ -> new UnsynchronizedSequence());
          // Write the img tag
          String refId = PageIndex.getRefIdInPage(request, dia.getPage(), dia.getId());
          final String urlPath;
          if (export != null) {
            urlPath = buildUrlPath(
              request,
              pageRef,
              width,
              height,
              PIXEL_DENSITIES[0],
              export
            );
          } else {
            urlPath =
              request.getContextPath()
              + MISSING_IMAGE_PATH
            ;
          }
          content.img()
            .id(refId)
            .src(response.encodeURL(URIEncoder.encodeURI(urlPath)))
            .width(
              export != null
              ? (export.getWidth() / PIXEL_DENSITIES[0])
              : width != 0
              ? width
              : (MISSING_IMAGE_WIDTH * height / MISSING_IMAGE_HEIGHT)
            ).height(
              export != null
              ? (export.getHeight() / PIXEL_DENSITIES[0])
              : height != 0
              ? height
              : (MISSING_IMAGE_HEIGHT * width / MISSING_IMAGE_WIDTH)
            ).alt(dia.getLabel())
          .__();
          //if (resourceFile == null) {
          //  LinkImpl.writeBrokenPathInXhtmlAttribute(pageRef, out);
          //} else {
          //  encodeTextInXhtmlAttribute(resourceFile.getName(), out);
          //}

          if (export != null && PIXEL_DENSITIES.length > 1) {
            assert resourceFile != null;
            assert exports != null;
            // Write links to the exports for higher pixel densities
            long[] altLinkNums = new long[PIXEL_DENSITIES.length];
            for (int i=0; i<PIXEL_DENSITIES.length; i++) {
              int pixelDensity = PIXEL_DENSITIES[i];
              // Get the thumbnail image in alternate pixel density
              DiaExport altExport = exports.get(i);
              // Write the a tag to additional pixel densities
              long altLinkNum = idSequence.getNextSequenceValue();
              altLinkNums[i] = altLinkNum;
              final String altUrlPath = buildUrlPath(
                request,
                pageRef,
                width,
                height,
                pixelDensity,
                altExport
              );
              content.a()
                .id(id -> id.append(ALT_LINK_ID_PREFIX).append(Long.toString(altLinkNum)))
                .style("display:none")
                .href(response.encodeURL(URIEncoder.encodeURI(altUrlPath)))
              .__(a -> a
                .text('x').text(pixelDensity)
              );
            }
            // Write script to hide alt links and select best based on device pixel ratio
            try (JavaScriptWriter script = content.script()._c()) {
              // hide alt links
              //for (int i=1; i<PIXEL_DENSITIES.length; i++) {
              //  long altLinkNum = altLinkNums[i];
              //  scriptOut
              //    .write("document.getElementById(\"" + ALT_LINK_ID_PREFIX)
              //    .write(Long.toString(altLinkNum))
              //    .write("\").style.display = \"none\";\n");
              //}
              // select best based on device pixel ratio
              script.write("if (window.devicePixelRatio) {\n");
              // Closure for locally scoped variables
              script.write("  (function () {\n");
              // scriptOut.write("  window.alert(\"devicePixelRatio=\" + window.devicePixelRatio);\n");
              // Function to update src
              script.write("    function updateImageSrc() {\n");
              for (int i=PIXEL_DENSITIES.length - 1; i >= 0; i--) {
                long altLinkNum = altLinkNums[i];
                script.write("      ");
                if (i != (PIXEL_DENSITIES.length - 1)) {
                  script.write("else ");
                }
                if (i > 0) {
                  script.write("if (window.devicePixelRatio > ");
                  script.write(Integer.toString(PIXEL_DENSITIES[i-1]));
                  script.write(") ");
                }
                script.append("{\n"
                    + "        document.getElementById(").text(refId).append(").src = document.getElementById(\"" + ALT_LINK_ID_PREFIX).append(Long.toString(altLinkNum)).append("\").getAttribute(\"href\");\n"
                    + "      }\n");
              }
              script.write("    }\n"
                // Perform initial setup
                + "    updateImageSrc();\n");
              // Change image source when pixel ratio changes
              script.write("    if (window.matchMedia) {\n");
              for (int i=0; i<PIXEL_DENSITIES.length; i++) {
                int pixelDensity = PIXEL_DENSITIES[i];
                script.write("      window.matchMedia(\"screen and (max-resolution: ");
                script.write(Integer.toString(pixelDensity));
                script.write("dppx)\").addListener(function(e) {\n"
                    + "        updateImageSrc();\n"
                    + "      });\n");
              }
              script.write("    }\n"
                + "  })();\n"
                + "}");
            }
          }
        }
      }
    } catch (InterruptedException e) {
      // Restore the interrupted status
      Thread.currentThread().interrupt();
      throw new ServletException(e);
    }
  }
}
