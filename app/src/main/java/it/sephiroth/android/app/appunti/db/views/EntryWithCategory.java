package it.sephiroth.android.app.appunti.db.views;

import android.provider.BaseColumns;
import com.dbflow5.annotation.Column;
import com.dbflow5.annotation.ModelView;
import com.dbflow5.annotation.ModelViewQuery;
import com.dbflow5.query.StringQuery;
import com.dbflow5.sql.Query;
import com.dbflow5.structure.BaseModelView;
import it.sephiroth.android.app.appunti.db.AppDatabase;
import it.sephiroth.android.app.appunti.db.CategoryTypeConverter;
import it.sephiroth.android.app.appunti.db.tables.Category;
import timber.log.Timber;

@ModelView(database = AppDatabase.class)
public class EntryWithCategory extends BaseModelView implements BaseColumns {

    @Column
    public int entriesCount = 0;

    @Column
    public Long categoryID;

    @Column
    public String categoryTitle;

    @Column
    public int categoryColorIndex;

    @Column(typeConverter = CategoryTypeConverter.class)
    public Category.CategoryType categoryType = Category.CategoryType.USER;


    public Category category() {
        Timber.i("converto to category = %s, %d, %s", categoryTitle, categoryColorIndex, categoryType);
        Category category = new Category(categoryTitle, categoryColorIndex, categoryType);
        category.setCategoryID(categoryID);
        return category;
    }


    @ModelViewQuery
    public static final Query query() {
        return buildQuery();
    }

    private static Query buildQuery() {
        String query = "select Category.*, (select count(*) from Entry where Entry.category_categoryID = Category.categoryID and Entry.entryDeleted = 0 and Entry.entryArchived = 0) as entriesCount from Category";
        return new StringQuery<>(Category.class, query);
    }

}
