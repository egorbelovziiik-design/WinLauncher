package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Keeps a copy of the books you have read. */
public final class BookReaderModule extends Module {

    private final BooleanSetting autoSave;
    private final NumberSetting maxBooks;
    private final List<String> books = new ArrayList<String>();

    public BookReaderModule() {
        super("bookreader", "BookReader", "Keeps a copy of the books you read.",
                ModuleCategory.MISC);
        this.autoSave = bool("Auto Save", "Save books when you open them", true);
        this.maxBooks = number("Max Books", "Maximum stored books", 50.0, 5.0, 500.0, 5.0);
    }

    public void save(String title, String content) {
        if (!autoSave.isEnabled()) {
            return;
        }
        books.add(0, title + "\n" + content);
        while (books.size() > maxBooks.value()) {
            books.remove(books.size() - 1);
        }
        markActivity();
    }

    public List<String> books() {
        return books;
    }

    public void clear() {
        books.clear();
    }

    @Override
    public String hudInfo() {
        return books.size() + " books";
    }
}
