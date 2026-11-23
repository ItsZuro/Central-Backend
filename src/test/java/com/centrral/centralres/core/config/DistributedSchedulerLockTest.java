package com.centrral.centralres.core.config;

import static org.mockito.Mockito.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

class DistributedSchedulerLockTest {
    @Test
    void localModeRunsWithoutDatabase() {
        DataSource db = mock(DataSource.class);
        Runnable job = mock(Runnable.class);
        new DistributedSchedulerLock(db, false).runOnce(1L, job);
        verify(job).run();
        verifyNoInteractions(db);
    }

    @Test
    void postgresModeAcquiresAndReleasesLock() throws Exception {
        DataSource db = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        Runnable job = mock(Runnable.class);
        when(db.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getBoolean(1)).thenReturn(true);

        new DistributedSchedulerLock(db, true).runOnce(8L, job);

        verify(job).run();
        verify(acquire).setLong(1, 8L);
        verify(release).setLong(1, 8L);
        verify(release).execute();
    }
}
