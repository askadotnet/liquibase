package liquibase.changelog.filter;

import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.RanChangeSet;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.executor.Executor;
import liquibase.executor.ExecutorService;
import liquibase.statement.core.UpdateStatement;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShouldRunChangeSetFilterTest  {

    private Database database = createMock(Database.class);

    @Test
    public void accepts_noneRun() throws DatabaseException {
        expect(database.getRanChangeSetList()).andReturn(new ArrayList<RanChangeSet>());
        replay(database);

        ShouldRunChangeSetFilter filter = new ShouldRunChangeSetFilter(database);

        assertTrue(filter.accepts(new ChangeSet("1", "testAuthor", false, false, "path/changelog", null, null, null)).isAccepted());
    }

    @Test
    public void accepts() throws DatabaseException {
        given_a_database_with_two_executed_changesets();

        ShouldRunChangeSetFilter filter = new ShouldRunChangeSetFilter(database);

        assertFalse("Already ran changeset should not be accepted", filter.accepts(new ChangeSet("1", "testAuthor", false, false, "path/changelog", null, null, null)).isAccepted());

        assertTrue("AlwaysRun changesets should always be accepted", filter.accepts(new ChangeSet("1", "testAuthor", true, false, "path/changelog", null, null, null)).isAccepted());

        assertTrue("RunOnChange changed changeset should be accepted", filter.accepts(new ChangeSet("1", "testAuthor", false, true, "path/changelog", null, null, null)).isAccepted());

        assertTrue("ChangeSet with different id should be accepted", filter.accepts(new ChangeSet("3", "testAuthor", false, false, "path/changelog", null, null, null)).isAccepted());

        assertTrue("ChangeSet with different author should be accepted", filter.accepts(new ChangeSet("1", "otherAuthor", false, false, "path/changelog", null, null, null)).isAccepted());

        assertTrue("ChangSet with different path should be accepted", filter.accepts(new ChangeSet("1", "testAuthor", false, false, "other/changelog", null, null, null)).isAccepted());
    }

    @Test
    public void does_NOT_accept_current_changeset_with_classpath_prefix() throws DatabaseException {
        given_a_database_with_two_executed_changesets();
        ChangeSet changeSetWithClasspathPrefix = new ChangeSet("1", "testAuthor", false, false, "classpath:path/changelog", null, null, null);

        ShouldRunChangeSetFilter filter = new ShouldRunChangeSetFilter(database, true);

        assertFalse(filter.accepts(changeSetWithClasspathPrefix).isAccepted());
    }

    @Test
    public void does_NOT_accept_current_changeset_when_inserted_changeset_has_classpath_prefix() throws DatabaseException {
        given_a_database_with_two_executed_changesets();
        ChangeSet changeSet = new ChangeSet("2", "testAuthor", false, false, "path/changelog", null, null, null);

        ShouldRunChangeSetFilter filter = new ShouldRunChangeSetFilter(database, true);

        assertFalse(filter.accepts(changeSet).isAccepted());
    }

    @Test
    public void does_NOT_accept_current_changeset_when_both_have_classpath_prefix() throws DatabaseException {
        given_a_database_with_two_executed_changesets();
        ChangeSet changeSet = new ChangeSet("2", "testAuthor", false, false, "classpath:path/changelog", null, null, null);

        ShouldRunChangeSetFilter filter = new ShouldRunChangeSetFilter(database, true);

        assertFalse(filter.accepts(changeSet).isAccepted());
    }

    private Database given_a_database_with_two_executed_changesets() throws DatabaseException {
        ArrayList<RanChangeSet> ranChanges = new ArrayList<RanChangeSet>();
        RanChangeSet ranChangeSet1 = new RanChangeSet("path/changelog", "1", "testAuthor", CheckSum.parse("12345"), new Date(), null, null, null, null, null, null);
        ranChangeSet1.setOrderExecuted(1);
        ranChanges.add(ranChangeSet1);
        RanChangeSet ranChangeSet2 = new RanChangeSet("classpath:path/changelog", "2", "testAuthor", CheckSum.parse("12345"), new Date(), null, null, null, null, null, null);
        ranChangeSet2.setOrderExecuted(2);
        ranChanges.add(ranChangeSet2);

        return mock_database(ranChanges);
    }

    private Database mock_database(List<RanChangeSet> ranChanges) throws DatabaseException {
        expect(database.getRanChangeSetList()).andReturn(ranChanges);
        expect(database.getDatabaseChangeLogTableName()).andReturn("DATABASECHANGELOG").anyTimes();
        expect(database.getDefaultSchemaName()).andReturn(null).anyTimes();

        Executor template = createMock(Executor.class);
        expect(template.update(isA(UpdateStatement.class))).andReturn(1).anyTimes();

        replay(database);
        replay(template);
        ExecutorService.getInstance().setExecutor(database, template);
        return database;
    }

    private Database given_a_database_with_one_twice_executed_changeset() throws DatabaseException {
        ArrayList<RanChangeSet> ranChanges = new ArrayList<RanChangeSet>();
        RanChangeSet ranChangeSet1 = new RanChangeSet("path/changelog", "1", "testAuthor", CheckSum.parse("not_matched_checksum"), new Date(), null, null, null, null, null, null);
        ranChangeSet1.setOrderExecuted(1);
        ranChanges.add(ranChangeSet1);
        RanChangeSet ranChangeSet2 = new RanChangeSet("path/changelog", "1", "testAuthor", CheckSum.parse("7:d41d8cd98f00b204e9800998ecf8427e"), new Date(), null, null, null, null, null, null);
        ranChangeSet2.setOrderExecuted(2);
        ranChanges.add(ranChangeSet2);

        return mock_database(ranChanges);
    }

    @Test
    public void should_decline_not_changed_changeset_when_has_run_on_change() throws DatabaseException {
        given_a_database_with_one_twice_executed_changeset();

        ShouldRunChangeSetFilter filter = new ShouldRunChangeSetFilter(database);

        assertFalse("RunOnChange not changed changeset should NOT be accepted", filter.accepts(new ChangeSet("1", "testAuthor", false, true, "path/changelog", null, null, null)).isAccepted());
    }
}
