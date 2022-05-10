package org.zkoss.support.patch;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.image.AImage;
import org.zkoss.lang.Strings;
import org.zkoss.sound.AAudio;
import org.zkoss.util.media.AMedia;
import org.zkoss.util.media.ContentTypes;
import org.zkoss.util.media.Media;
import org.zkoss.video.AVideo;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.WebApps;
import org.zkoss.zk.ui.util.CharsetFinder;
import org.zkoss.zk.ui.util.Configuration;

/**
 * The file item factory that monitors the progress of uploading.
 */
public class UploadFixItemFactory implements org.zkoss.zk.ui.sys.DiskFileItemFactory {
	private static final Logger log = LoggerFactory.getLogger(UploadFixItemFactory.class);

	@Override
	public FileItem createItem(String fieldName, String contentType, boolean isFormField, String fileName,
			int sizeThreshold, File repository) {
		if("nextURI".equals(fieldName))
			throw new RuntimeException("Field not allowed");
		return new ZkFileItem(fieldName, contentType, isFormField, fileName, sizeThreshold, repository);
	}

	@Override
	public Media createMedia(FileItem fi, String contentType, String name, boolean alwaysNative) {
		String ctype = fi.getContentType(),
				ctypelc = ctype != null ? ctype.toLowerCase(java.util.Locale.ENGLISH) : null;
		if (!alwaysNative && ctypelc != null) {
			if (ctypelc.startsWith("image/")) {
				try {
					return fi.isInMemory() ? new AImage(name, fi.get()) : new AImage(name, fi.getInputStream());
					//note: AImage converts stream to binary array
				} catch (Throwable ex) {
					if (log.isDebugEnabled())
						log.debug("Unknown file format: " + ctype);
				}
			} else if (ctypelc.startsWith("audio/")) {
				try {
					return fi.isInMemory() ? new AAudio(name, fi.get()) : new StreamAudio(name, fi, ctypelc);
				} catch (Throwable ex) {
					if (log.isDebugEnabled())
						log.debug("Unknown file format: " + ctype);
				}
			} else if (ctypelc.startsWith("video/")) {
				try {
					return fi.isInMemory() ? new AVideo(name, fi.get()) : new StreamVideo(name, fi, ctypelc);
				} catch (Throwable ex) {
					if (log.isDebugEnabled())
						log.debug("Unknown file format: " + ctype);
				}
			} else if (ctypelc.startsWith("text/")) {
				String charset = getCharset(ctype);
				if (charset == null) {
					final Configuration conf = WebApps.getCurrent().getConfiguration();
					final CharsetFinder chfd = conf.getUploadCharsetFinder();
					if (chfd != null)
						try {
							charset = chfd.getCharset(ctype,
									fi.isInMemory() ? new ByteArrayInputStream(fi.get()) : fi.getInputStream());
						} catch (IOException e) {
							e.printStackTrace();
						}
					if (charset == null)
						charset = conf.getUploadCharset();
				}
				try {
					return fi.isInMemory() ? new AMedia(name, null, ctype, fi.getString(charset))
							: new ReaderMedia(name, null, ctype, fi, charset);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}
		}

		return fi.isInMemory() ? new AMedia(name, null, ctype, fi.get()) : new StreamMedia(name, null, ctype, fi);
	}

	class ZkFileItem extends DiskFileItem {
		public ZkFileItem(String fieldName, String contentType, boolean isFormField, String fileName,
				int sizeThreshold, File repository) {
			super(fieldName, contentType, isFormField, fileName, sizeThreshold, repository);
		}
	}
	
	private static class StreamMedia extends AMedia {
		private final FileItem _fi;

		public StreamMedia(String name, String format, String ctype, FileItem fi) {
			super(name, format, ctype, DYNAMIC_STREAM);
			_fi = fi;
		}

		public java.io.InputStream getStreamData() {
			try {
				return _fi.getInputStream();
			} catch (IOException ex) {
				throw new UiException("Unable to read " + _fi, ex);
			}
		}

		public boolean isBinary() {
			return true;
		}

		public boolean inMemory() {
			return false;
		}
	}
	
	private static class ReaderMedia extends AMedia {
		private final FileItem _fi;
		private final String _charset;

		public ReaderMedia(String name, String format, String ctype, FileItem fi, String charset) {
			super(name, format, ctype, DYNAMIC_READER);
			_fi = fi;
			_charset = charset;
		}

		public java.io.Reader getReaderData() {
			try {
				return new java.io.InputStreamReader(_fi.getInputStream(), _charset);
			} catch (IOException ex) {
				throw new UiException("Unable to read " + _fi, ex);
			}
		}

		public boolean isBinary() {
			return false;
		}

		public boolean inMemory() {
			return false;
		}
	}

	private static class StreamAudio extends AAudio {
		private final FileItem _fi;
		private String _format;
		private String _ctype;

		public StreamAudio(String name, FileItem fi, String ctype) throws IOException {
			super(name, DYNAMIC_STREAM);
			_fi = fi;
			_ctype = ctype;
		}

		public java.io.InputStream getStreamData() {
			try {
				return _fi.getInputStream();
			} catch (IOException ex) {
				throw new UiException("Unable to read " + _fi, ex);
			}
		}

		public String getFormat() {
			if (_format == null) {
				_format = ContentTypes.getFormat(getContentType());
			}
			return _format;
		}

		public String getContentType() {
			return _ctype != null ? _ctype : _fi.getContentType();
		}
	}
	
	private static class StreamVideo extends AVideo {
		private final FileItem _fi;
		private String _format;
		private String _ctype;

		public StreamVideo(String name, FileItem fi, String ctype) throws IOException {
			super(name, DYNAMIC_STREAM);
			_fi = fi;
			_ctype = ctype;
		}

		public java.io.InputStream getStreamData() {
			try {
				return _fi.getInputStream();
			} catch (IOException ex) {
				throw new UiException("Unable to read " + _fi, ex);
			}
		}

		public String getFormat() {
			if (_format == null) {
				_format = ContentTypes.getFormat(getContentType());
			}
			return _format;
		}

		public String getContentType() {
			return _ctype != null ? _ctype : _fi.getContentType();
		}
	}
	
	private static String getCharset(String ctype) {
		final String ctypelc = ctype.toLowerCase(java.util.Locale.ENGLISH);
		for (int j = 0; (j = ctypelc.indexOf("charset", j)) >= 0; j += 7) {
			int k = Strings.skipWhitespacesBackward(ctype, j - 1);
			if (k < 0 || ctype.charAt(k) == ';') {
				k = Strings.skipWhitespaces(ctype, j + 7);
				if (k <= ctype.length() && ctype.charAt(k) == '=') {
					j = ctype.indexOf(';', ++k);
					String charset = (j >= 0 ? ctype.substring(k, j) : ctype.substring(k)).trim();
					if (charset.length() > 0)
						return charset;
					break; //use default
				}
			}
		}
		return null;
	}
}