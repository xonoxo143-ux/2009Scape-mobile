package net.kdt.pojavlaunch;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Exposes only the single-player server directory through Android's Documents UI.
 * The actual files remain app-owned and persist across APK updates.
 */
public class ServerFilesProvider extends DocumentsProvider {
    public static final String ROOT_ID = "singleplayer";
    public static final String ROOT_DOCUMENT_ID = ROOT_ID + ":";
    public static final String DIRECTORY_NAME = "singleplayer-server";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    public static String getAuthority(Context context) {
        return context.getPackageName() + ".serverfiles";
    }

    public static File ensureServerRoot(Context context) {
        File root = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Unable to create local server directory: " + root);
        }
        return root;
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        ensureServerRoot(context);
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        Context context = getContext();
        MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        if (context == null) return result;

        File root = ensureServerRoot(context);
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID);
        row.add(DocumentsContract.Root.COLUMN_TITLE, "2009Scape Server Files");
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Single-player saves, configs, logs and server data");
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE |
                DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD |
                DocumentsContract.Root.FLAG_LOCAL_ONLY);
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, root.getUsableSpace());
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, documentId, fileForDocumentId(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        File parent = fileForDocumentId(parentDocumentId);
        if (!parent.isDirectory()) throw new FileNotFoundException("Not a directory: " + parentDocumentId);

        File[] children = parent.listFiles();
        if (children != null) {
            for (File child : children) {
                includeFile(result, documentIdForFile(child), child);
            }
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File file = fileForDocumentId(documentId);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = fileForDocumentId(parentDocumentId);
        if (!parent.isDirectory()) throw new FileNotFoundException("Not a directory: " + parentDocumentId);

        String safeName = displayName == null ? "new-file" : new File(displayName).getName();
        File target = new File(parent, safeName);
        ensureInsideRoot(target);
        try {
            boolean created;
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                created = target.mkdir();
            } else {
                created = target.createNewFile();
            }
            if (!created) throw new IOException("File already exists or could not be created");
            return documentIdForFile(target);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = fileForDocumentId(documentId);
        if (ROOT_DOCUMENT_ID.equals(documentId)) {
            throw new FileNotFoundException("Cannot delete the server root");
        }
        if (!deleteRecursively(file)) {
            throw new FileNotFoundException("Unable to delete " + documentId);
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File source = fileForDocumentId(documentId);
        if (ROOT_DOCUMENT_ID.equals(documentId)) {
            throw new FileNotFoundException("Cannot rename the server root");
        }
        String safeName = displayName == null ? source.getName() : new File(displayName).getName();
        File target = new File(source.getParentFile(), safeName);
        ensureInsideRoot(target);
        if (!source.renameTo(target)) {
            throw new FileNotFoundException("Unable to rename " + documentId);
        }
        return documentIdForFile(target);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            File parent = fileForDocumentId(parentDocumentId).getCanonicalFile();
            File child = fileForDocumentId(documentId).getCanonicalFile();
            String parentPath = parent.getPath() + File.separator;
            return child.equals(parent) || child.getPath().startsWith(parentPath);
        } catch (IOException e) {
            return false;
        }
    }

    private void includeFile(MatrixCursor cursor, String documentId, File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        boolean directory = file.isDirectory();
        int flags;
        if (directory) {
            flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE |
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE |
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
        } else {
            flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE |
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
        }

        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ROOT_DOCUMENT_ID.equals(documentId) ? "2009Scape Server Files" : file.getName());
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                directory ? DocumentsContract.Document.MIME_TYPE_DIR : mimeTypeFor(file));
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
        row.add(DocumentsContract.Document.COLUMN_SIZE, directory ? null : file.length());
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    private File fileForDocumentId(String documentId) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Provider context unavailable");
        if (documentId == null || !documentId.startsWith(ROOT_DOCUMENT_ID)) {
            throw new FileNotFoundException("Unknown document: " + documentId);
        }

        File root = ensureServerRoot(context);
        String relative = documentId.substring(ROOT_DOCUMENT_ID.length());
        File file = relative.isEmpty() ? root : new File(root, relative);
        ensureInsideRoot(file);
        if (!file.exists()) throw new FileNotFoundException("Missing document: " + documentId);
        return file;
    }

    private String documentIdForFile(File file) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Provider context unavailable");
        try {
            File root = ensureServerRoot(context).getCanonicalFile();
            File canonical = file.getCanonicalFile();
            if (canonical.equals(root)) return ROOT_DOCUMENT_ID;
            String prefix = root.getPath() + File.separator;
            if (!canonical.getPath().startsWith(prefix)) {
                throw new FileNotFoundException("Path is outside the server root");
            }
            return ROOT_DOCUMENT_ID + canonical.getPath().substring(prefix.length()).replace(File.separatorChar, '/');
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private void ensureInsideRoot(File file) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Provider context unavailable");
        try {
            File root = ensureServerRoot(context).getCanonicalFile();
            File canonical = file.getCanonicalFile();
            String prefix = root.getPath() + File.separator;
            if (!canonical.equals(root) && !canonical.getPath().startsWith(prefix)) {
                throw new FileNotFoundException("Path traversal blocked");
            }
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }

    private static String mimeTypeFor(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
            if (type != null) return type;
        }
        return "application/octet-stream";
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection == null ? DEFAULT_ROOT_PROJECTION : projection;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection == null ? DEFAULT_DOCUMENT_PROJECTION : projection;
    }
}
