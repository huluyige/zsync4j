/**
 * Copyright (c) 2015, Salesforce.com, Inc. All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * <p>
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * <p>
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * <p>
 * Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.zsync;

import com.salesforce.zsync.http.Credentials;
import com.salesforce.zsync.internal.*;
import com.salesforce.zsync.internal.util.*;
import com.salesforce.zsync.internal.util.HttpClient.HttpError;
import com.salesforce.zsync.internal.util.HttpClient.HttpTransferListener;
import com.salesforce.zsync.internal.util.ObservableRedableByteChannel.ObservableReadableResourceChannel;
import com.salesforce.zsync.internal.util.TransferListener.ResourceTransferListener;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.zsync.internal.util.HttpClient.newHttpClient;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * Zsync download client: reduces the number of bytes retrieved from a remote server by drawing unchanged parts of the
 * file from a set of local input files.
 *
 * @author bbusjaeger
 * @see <a href="http://zsync.moria.org.uk/">http://zsync.moria.org.uk/</a>
 */
public class Zsync {

    public static final String VERSION = "0.6.2";
    private final HttpClient httpClient;

    /**
     * Creates a new zsync client
     */
    public Zsync() {
        this(newHttpClient());
    }

    /**
     * Creates a new zsync client that reuses a clone of the given http client.
     *
     * @param okHttpClient
     */
    public Zsync(OkHttpClient okHttpClient) {
        this(newHttpClient(okHttpClient));
    }

    /* currently internal as HttpClient not exposed */
    private Zsync(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Pads the given channel with zeros if the length of the input file is not evenly divisible by the block size. The is
     * necessary to match how the checksums in the zsync file are computed.
     *
     * @param channel channel for input file to pad
     * @param header  header of the zsync file being processed.
     * @return
     * @throws IOException
     */
    static ReadableByteChannel zeroPad(ReadableByteChannel channel, long size, int matcherBlockSize, Header header)
            throws IOException {
        final int numZeros;
        if (size < matcherBlockSize) {
            numZeros = matcherBlockSize - (int) size;
        } else {
            final int blockSize = header.getBlocksize();
            final int lastBlockSize = (int) (size % blockSize);
            numZeros = lastBlockSize == 0 ? 0 : blockSize - lastBlockSize;
        }
        return numZeros == 0 ? channel : new ZeroPaddedReadableByteChannel(channel, numZeros);
    }

    // this is just a temporary hacked up CLI for testing purposes
    public static void main(String[] args) throws IOException, ZsyncException {
//        if (args.length == 0) {
//            throw new IllegalArgumentException("Must specify at least zsync file url");
//        }
//
//        if (args.length % 2 == 0) {
//            throw new IllegalArgumentException("Must specify pairs of args");
//        }
//
//        final FileSystem fs = FileSystems.getDefault();
//        final Options options = new Options();
//        for (int i = 0; i < args.length - 1; i++) {
//            if ("-A".equals(args[i])) {
//                final String auth = args[++i];
//                final int eq, cl;
//                if ((eq = auth.indexOf('=')) > 0 && (cl = auth.indexOf(':', eq + 1)) > 0) {
//                    options.putCredentials(auth.substring(0, eq),
//                            new Credentials(auth.substring(eq + 1, cl), auth.substring(cl + 1)));
//                } else {
//                    throw new IllegalArgumentException("authenticator must be of form 'hostname=username:password'");
//                }
//            } else if ("-i".equals(args[i])) {
//                options.addInputFile(fs.getPath(args[++i]));
//            } else if ("-o".equals(args[i])) {
//                options.setOutputFile(fs.getPath(args[++i]));
//            }
//        }
//        final URI uri = URI.create(args[args.length - 1]);
//
//        final Zsync zsync = new Zsync(new OkHttpClient());
//        final ZsyncStatsObserver observer = new ZsyncStatsObserver();
//        zsync.zsync(uri, options, observer);
//        final ZsyncStats stats = observer.build();
//        System.out.println("Total bytes written: " + stats.getTotalBytesWritten() + " (by input file: "
//                + stats.getTotalBytesWrittenByInputFile() + ")");
//        System.out.println("Total bytes read: " + stats.getTotalBytesRead() + " (by input file: "
//                + stats.getTotalBytesReadByInputFile() + ")");
//        System.out.println("Total bytes downloaded: " + stats.getTotalBytesDownloaded() + " (control file: "
//                + stats.getBytesDownloadedForControlFile() + ", remote file: " + stats.getBytesDownloadedFromRemoteFile()
//                + ")");
//        System.out.println("Total time: " + stats.getTotalElapsedMilliseconds() + " ms. Of which downloading "
//                + stats.getElapsedMillisecondsDownloading() + " ms");
    }

    /**
     * Convenience method for {@link #zsync(URI, Options, ZsyncObserver)} without options or observer. The URI passed to
     * this method must be an absolute HTTP URL. The output file location will be derived from the filename header in the
     * zsync control file as described in {@link Options#getOutputFile(File)}. If the output file already exists, zsync
     * will reuse unchanged blocks, download only changed blocks, and replace the output file upon successful completion.
     * Otherwise, zsync will download the full content from the remote server.
     *
     * @param zsyncFile Absolute HTTP URL of the zsync control file generated by {@link ZsyncMake}
     * @return Path location of the written output file
     * @throws ZsyncException if an unexpected error occurs
     */
    public File zsync(URI zsyncFile) throws ZsyncException {
        return this.zsync(zsyncFile, null);
    }

    /**
     * Convenience method for {@link #zsync(URI, Options)} without observer.
     *
     * @param zsyncFile URI of the zsync control file generated for the target file by {@link ZsyncMake}
     * @param options   Optional parameters to the zsync operation
     * @return Path location of the written output file
     * @throws ZsyncException if an unexpected error occurs
     */
    public File zsync(URI zsyncFile, Options options) throws ZsyncException {
        return this.zsync(zsyncFile, options, null);
    }

    /**
     * Runs zsync to delta-download an http file per {@see http://zsync.moria.org.uk/}.
     * <p>
     * <p>
     * The zsyncFile parameter must point to the zsync control file generated by {@link ZsyncMake}. Typically, this will
     * be an absolute HTTP URI, but it can also be an absolute or relative file system URI in case the file has already
     * been downloaded. The URI of the actual file to download is determined from the <code>URI</code> header value in the
     * zsync control file. If that value is a relative URI, it is resolved against the remote zsync file URI. For example,
     * if the control file is located at <code>http://myhost.com/file.zsync</code> and the header value is
     * <code>file</code>, then the resolved URI is <code>http://myhost.com/file</code>. Note that if the zsyncFile
     * parameter is a local file system URI and the URI header value is a relative URI, the
     * {@link Options#getZsyncFileSource()} option must be set to resolve the absolute URI.
     * </p>
     * <p>
     * The options parameter is optional, i.e. it may be null or empty. It can be used to pass optional parameters to
     * zsync per the documentation on the get and set methods on the {@link Options} class. For example, additional input
     * files can be specified via {@link Options#addInputFile(File)}; the output location can be set via
     * {@link Options#setOutputFile(File)}.
     * </p>
     * <p>
     * The observer parameter is also optional. Passing a {@link ZsyncObserver} observer enables fine-grained progress and
     * statistics reporting. For the latter {@link ZsyncStatsObserver} can be used directly.
     * </p>
     *
     * @param zsyncFile URI of the zsync control file generated for the target file by {@link ZsyncMake}
     * @param options
     * @param observer
     * @return
     * @throws ZsyncException
     */
    public File zsync(URI zsyncFile, Options options, ZsyncObserver observer) throws ZsyncException {
        final EventDispatcher events = new EventDispatcher(observer == null ? new ZsyncObserver() : observer);
        try {
            options = new Options(options); // Copy, since the supplied Options object is mutable
            events.zsyncStarted(zsyncFile, options);
            return this.zsyncInternal(zsyncFile, options, events);
        } catch (ZsyncException | RuntimeException exception) {
            events.zsyncFailed(exception);
            throw exception;
        } finally {
            events.zsyncComplete();
        }
    }

    private File zsyncInternal(URI zsyncFile, Options options, EventDispatcher events) throws ZsyncException {
        final ControlFile controlFile;
        try (InputStream in = this.openZsyncFile(zsyncFile, this.httpClient, options, events)) {
            controlFile = ControlFile.read(in);
        } catch (HttpError e) {
            if (e.getCode() == HTTP_NOT_FOUND) {
                throw new ZsyncControlFileNotFoundException("Zsync file " + zsyncFile + " does not exist.", e);
            }
            throw new ZsyncException("Unexpected Http error retrieving zsync file", e);
//        } catch (NoSuchFileException e) {
//            throw new ZsyncControlFileNotFoundException("Zsync file " + zsyncFile + " does not exist.", e);
        } catch (IOException e) {
            throw new ZsyncException("Failed to read zsync control file", e);
        }

        // determine output file location
        File outputFile = options.getOutputFile();
        if (outputFile == null) {
            outputFile = new File(controlFile.getHeader().getFilename());
            //outputFile = Paths.get(controlFile.getHeader().getFilename());
        }

        // use the output file as a seed if it already exists
        if (outputFile.exists()) {
            options.getInputFiles().add(outputFile);
        }

        // determine remote file location
        URI remoteFileUri = URI.create(controlFile.getHeader().getUrl());
        if (!remoteFileUri.isAbsolute()) {
            if (options.getZsyncFileSource() == null) {
                throw new IllegalArgumentException(
                        "Remote file path is relative, but no zsync file source URI set to resolve it");
            }
            remoteFileUri = options.getZsyncFileSource().resolve(remoteFileUri);
        }

        try (final OutputFileWriter outputFileWriter =
                     new OutputFileWriter(outputFile, controlFile, events.getOutputFileWriteListener())) {
            if (!this.processInputFiles(outputFileWriter, controlFile, options.getInputFiles(), events)) {
                this.httpClient.partialGet(remoteFileUri, outputFileWriter.getMissingRanges(), options.getCredentials(),
                        events.getRangeReceiverListener(outputFileWriter), events.getRemoteFileDownloadListener());
            }
        } catch (ChecksumValidationIOException exception) {
            throw new ZsyncChecksumValidationFailedException("Calculated checksum does not match expected checksum");
        } catch (IOException | HttpError e) {
            throw new ZsyncException(e);
        }

        return outputFile;
    }

    /**
     * Opens the zsync file referred to by the given URI for read. If the file refers to a local file system path, the
     * local file is opened directly. Otherwise, if the file is remote and {@link Options#getSaveZsyncFile()} is
     * specified, the remote file is stored locally in the given location first and then opened for read locally. If the
     * file is remote and no save location is specified, the file is opened for read over the remote connection.
     * <p>
     * If the file is remote, the method always calls {@link Options#setZsyncFileSource(URI)} on the passed in options
     * parameter, so that relative file URLs in the control file can later be resolved against it.
     *
     * @param zsyncFile
     * @param httpClient
     * @param options
     * @return
     * @throws IOException
     * @throws HttpError
     */
    private InputStream openZsyncFile(URI zsyncFile, HttpClient httpClient, Options options, EventDispatcher events)
            throws IOException, HttpError {
        final InputStream in;
        if (zsyncFile.isAbsolute()) {
            // check if it's a local URI
            final File path = ZsyncUtil.getFile(zsyncFile);
            if (path == null) {
                // TODO we may want to set the redirect URL resulting from processing the http request
                options.setZsyncFileSource(zsyncFile);
                final HttpTransferListener listener = events.getControlFileDownloadListener();
                final Map<String, Credentials> credentials = options.getCredentials();
                // check if we should persist the file locally
                final File savePath = options.getSaveZsyncFile();
                if (savePath == null) {
                    in = httpClient.get(zsyncFile, credentials, listener);
                } else {
                    httpClient.get(zsyncFile, savePath, credentials, listener);
                    in = this.openZsyncFile(savePath, events);
                }
            } else {
                in = this.openZsyncFile(path, events);
            }
        } else {
            final String path = zsyncFile.getPath();
            if (path == null) {
                throw new IllegalArgumentException("Invalid zsync file URI: path of relative URI missing");
            }
            in = this.openZsyncFile(new File(path), events);
        }
        return in;
    }

    private InputStream openZsyncFile(File zsyncFile, EventDispatcher events) throws IOException {
        return new ObservableInputStream(new FileInputStream(zsyncFile), events.getControlFileReadListener());
    }

    private boolean processInputFiles(OutputFileWriter targetFile, ControlFile controlFile,
                                      Iterable<? extends File> inputFiles, EventDispatcher events) throws IOException {
        for (File inputFile : inputFiles) {
            if (this.processInputFile(targetFile, controlFile, inputFile, events.getInputFileReadListener())) {
                return true;
            }
        }
        return false;
    }

    private boolean processInputFile(OutputFileWriter targetFile, ControlFile controlFile, File inputFile,
                                     ResourceTransferListener<File> listener) throws IOException {
        final long size;
        try (final FileChannel fileChannel = new FileInputStream(inputFile).getChannel();
             //FileChannel.open(inputFile);
             final ReadableByteChannel channel =
                     new ObservableReadableResourceChannel<>(fileChannel, listener, inputFile, size = fileChannel.size())) {
            final BlockMatcher matcher = BlockMatcher.create(controlFile);
            final int matcherBlockSize = matcher.getMatcherBlockSize();
            final ReadableByteChannel c = zeroPad(channel, size, matcherBlockSize, controlFile.getHeader());
            final RollingBuffer buffer = new RollingBuffer(c, matcherBlockSize, 16 * matcherBlockSize);
            int bytes;
            do {
                bytes = matcher.match(targetFile, buffer);
            } while (buffer.advance(bytes));
        }
        return targetFile.isComplete();
    }

    /**
     * Optional arguments to the zsync client.
     *
     * @author bbusjaeger
     * @see <a href="http://linux.die.net/man/1/zsync">zsync(1) - Linux man page</a>
     */
    public static class Options {

        private List<File> inputFiles = new ArrayList<>(2);
        private File outputFile;
        private File saveZsyncFile;
        private URI zsyncUri;
        private Map<String, Credentials> credentials = new HashMap<>(2);

        public Options() {
            super();
        }

        public Options(Options other) {
            if (other != null) {
                this.inputFiles.addAll(other.getInputFiles());
                this.outputFile = other.outputFile;
                this.saveZsyncFile = other.saveZsyncFile;
                this.zsyncUri = other.zsyncUri;
                this.credentials.putAll(other.credentials);
            }
        }

        /**
         * Corresponds to zsync -i parameter: location of an input file from which to directly copy matching blocks to the
         * output file to reduce the number of bytes that have to be fetched from the remote source.
         *
         * @param inputFile
         * @return
         */
        public Options addInputFile(File inputFile) {
            this.inputFiles.add(inputFile);
            return this;
        }

        /**
         * Input files to construct output file from. If empty and the output file does not yet exist, the full content is
         * retrieved from the remote location.
         *
         * @return
         */
        public List<File> getInputFiles() {
            return this.inputFiles;
        }

        /**
         * Location at which to store the output file. If not set, output will be stored in the current working directory
         * using the <code>Filename</code> header from the control file as the relative path. If the output file already
         * exists, it will also be used as an input file to reuse matching blocks. Upon completion of the zsync operation,
         * the output file is atomically replaced with the new content (with fall-back to non-atomic in case the file system
         * does not support it).
         *
         * @return
         */
        public File getOutputFile() {
            return this.outputFile;
        }

        /**
         * Corresponds to zsync -o parameter: the location at which to store the output file.
         *
         * @param outputFile
         * @return
         */
        public Options setOutputFile(File outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        /**
         * The location at which to persist the zsync file if remote, may be null
         *
         * @return
         */
        public File getSaveZsyncFile() {
            return this.saveZsyncFile;
        }

        /**
         * Corresponds to the zsync -k parameter: the location at which to store the zsync control file. This option only
         * takes effect if the zsync URI passed as the first argument to {@link Zsync#zsync(URI, Options)} is a remote
         * (http) URL.
         *
         * @param saveZsyncFile
         * @return
         */
        public Options setSaveZsyncFile(File saveZsyncFile) {
            this.saveZsyncFile = saveZsyncFile;
            return this;
        }

        /**
         * The remote URI from which the local zsync file was originally retrieved from
         *
         * @return
         */
        public URI getZsyncFileSource() {
            return this.zsyncUri;
        }

        /**
         * Corresponds to the zsync -u parameter: the source URI from which the zsync file was originally retrieved. Takes
         * affect only if the first parameter to the {@link Zsync#zsync(URI, Options)} method refers to a local file.
         *
         * @param zsyncUri
         * @return
         */
        public Options setZsyncFileSource(URI zsyncUri) {
            this.zsyncUri = zsyncUri;
            return this;
        }

        /**
         * Registers the given credentials for the given host name. A {@link Zsync} instance applies the credentials as
         * follows:
         * <ol>
         * <li>The first request issued to a given host is sent without any form of authentication information to give the
         * server the opportunity to challenge the request.</li>
         * <li>If a 401 Basic authentication challenge is received and credentials for the given host are specified in the
         * options, the request is retried with a basic Authorization header.</li>
         * <li>Subsequent https requests to the same host are sent with a basic Authorization header in the first request.
         * This challenge caching is per host an does not take realms received as part of challenge responses into account.
         * Challenge caching is disabled for http requests to give the server an opportunity to redirect to https.</li>
         * </ol>
         *
         * @param hostname
         * @param credentials
         * @return
         */
        public Options putCredentials(String hostname, Credentials credentials) {
            this.credentials.put(hostname, credentials);
            return this;
        }

        /**
         * Registered credentials
         *
         * @return
         */
        public Map<String, Credentials> getCredentials() {
            return this.credentials;
        }

    }
}
