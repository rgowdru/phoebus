package org.phoebus.applications.filebrowser;

import java.io.File;
import java.util.List;

import org.phoebus.framework.jobs.JobManager;
import org.phoebus.ui.application.PhoebusApplication;
import org.phoebus.ui.dialog.ExceptionDetailsErrorDialog;
import org.phoebus.ui.javafx.ImageCache;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

@SuppressWarnings("nls")
final class FileTreeCell extends TreeCell<File> {
    private static final Image folder = ImageCache.getImage(PhoebusApplication.class, "/icons/fldr_obj.png");

    private static final Border BORDER = new Border(new BorderStroke(Color.GREEN, BorderStrokeStyle.SOLID,
                                                    new CornerRadii(5.0), BorderStroke.THIN));

    public FileTreeCell()
    {
        enableDragDrop();
    }

    private void enableDragDrop()
    {
        // Allow dragging this file
        setOnDragDetected(event ->
        {
            final File file = getItem();
            if (file != null)
            {
                final ClipboardContent content = new ClipboardContent();
                content.putFiles(List.of(file));
                content.putString(file.getAbsolutePath());

                final Dragboard db = startDragAndDrop(TransferMode.MOVE);
                db.setContent(content);
            }
            event.consume();
        });

        // Has file been moved elsewhere?
        setOnDragDone(event ->
        {
            final File file = getItem();
            if (event.getTransferMode() == TransferMode.MOVE  &&
                file != null  &&   ! file.exists())
            {
                // System.out.println("Delete tree item for removed " + file);
                final TreeItem<File> deleted_item = getTreeItem();
                deleted_item.getParent().getChildren().remove(deleted_item);
            }
            event.consume();
        });

        // Indicate if file may be dropped
        setOnDragOver(event ->
        {
            final File file = getItem();
            if (file != null  &&   event.getDragboard().hasFiles())
            {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                setBorder(BORDER);
            }
            event.consume();
        });
        setOnDragExited(event ->
        {
            setBorder(null);
            event.consume();
        });

        // A file has been dropped into this dir, or this file's directory
        setOnDragDropped(event ->
        {
            File dir = getItem();
            if (dir != null)
            {
                if (! dir.isDirectory())
                    dir = dir.getParentFile();
                final Dragboard db = event.getDragboard();
                if (db.hasFiles())
                    for (File file : db.getFiles())
                        move(file, dir);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /** @param file File to move
     *  @param dir Destination directory
     */
    private void move(final File file, final File dir)
    {
        // Ignore NOP move
        if (file.getParentFile().equals(dir))
            return;

        JobManager.schedule("Move file", monitor ->
        {
            // System.out.println("Move " + file + " into " + dir);
            final File new_name = new File(dir, file.getName());
            final boolean success = file.renameTo(new_name);
            Platform.runLater(() ->
            {
                if (success)
                {
                    // System.out.println("Add tree item for " + new_name);
                    final ObservableList<TreeItem<File>> siblings = getTreeItem().getParent().getChildren();
                    siblings.sort((a, b) -> a.getValue().getName().compareTo(b.getValue().getName()));
                    siblings.add(new FileTreeItem(new_name));
                }
                else
                {
                    final TreeView<File> tree = getTreeView();
                    ExceptionDetailsErrorDialog.openError(tree, "Error", "Failed to move\n" + file + " to " + dir, null);
                    // Force full refresh
                    tree.setRoot(new FileTreeItem(tree.getRoot().getValue()));
                }
            });
        });
    }

    @Override
    protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);

        if (empty || file == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (getTreeItem().getParent() == null) {
                // Root
                setText(file.getAbsolutePath());
            } else {
                if (file.isDirectory())
                    setGraphic(new ImageView(folder));
                else
                    setGraphic(null);
                setText(file.getName());
            }
        }
    }
}