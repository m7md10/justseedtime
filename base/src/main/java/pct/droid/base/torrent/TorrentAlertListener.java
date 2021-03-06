/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package pct.droid.base.torrent;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Logger;
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AnonymousModeAlert;
import com.frostwire.jlibtorrent.alerts.BlockDownloadingAlert;
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert;
import com.frostwire.jlibtorrent.alerts.BlockTimeoutAlert;
import com.frostwire.jlibtorrent.alerts.CacheFlushedAlert;
import com.frostwire.jlibtorrent.alerts.DhtReplyAlert;
import com.frostwire.jlibtorrent.alerts.FastresumeRejectedAlert;
import com.frostwire.jlibtorrent.alerts.FileCompletedAlert;
import com.frostwire.jlibtorrent.alerts.FileErrorAlert;
import com.frostwire.jlibtorrent.alerts.FileRenameFailedAlert;
import com.frostwire.jlibtorrent.alerts.FileRenamedAlert;
import com.frostwire.jlibtorrent.alerts.HashFailedAlert;
import com.frostwire.jlibtorrent.alerts.InvalidRequestAlert;
import com.frostwire.jlibtorrent.alerts.LsdPeerAlert;
import com.frostwire.jlibtorrent.alerts.MetadataFailedAlert;
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert;
import com.frostwire.jlibtorrent.alerts.PeerBanAlert;
import com.frostwire.jlibtorrent.alerts.PeerBlockedAlert;
import com.frostwire.jlibtorrent.alerts.PeerConnectAlert;
import com.frostwire.jlibtorrent.alerts.PeerDisconnectedAlert;
import com.frostwire.jlibtorrent.alerts.PeerErrorAlert;
import com.frostwire.jlibtorrent.alerts.PeerSnubbedAlert;
import com.frostwire.jlibtorrent.alerts.PeerUnsnubbedAlert;
import com.frostwire.jlibtorrent.alerts.PerformanceAlert;
import com.frostwire.jlibtorrent.alerts.PieceFinishedAlert;
import com.frostwire.jlibtorrent.alerts.ReadPieceAlert;
import com.frostwire.jlibtorrent.alerts.RequestDroppedAlert;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataFailedAlert;
import com.frostwire.jlibtorrent.alerts.ScrapeFailedAlert;
import com.frostwire.jlibtorrent.alerts.ScrapeReplyAlert;
import com.frostwire.jlibtorrent.alerts.StateChangedAlert;
import com.frostwire.jlibtorrent.alerts.StatsAlert;
import com.frostwire.jlibtorrent.alerts.StorageMovedAlert;
import com.frostwire.jlibtorrent.alerts.StorageMovedFailedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAddedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.alerts.TorrentCheckedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentDeleteFailedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentDeletedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentErrorAlert;
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentNeedCertAlert;
import com.frostwire.jlibtorrent.alerts.TorrentPausedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentPrioritizeAlert;
import com.frostwire.jlibtorrent.alerts.TorrentRemovedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentResumedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentUpdateAlert;
import com.frostwire.jlibtorrent.alerts.TrackerAnnounceAlert;
import com.frostwire.jlibtorrent.alerts.TrackerErrorAlert;
import com.frostwire.jlibtorrent.alerts.TrackerReplyAlert;
import com.frostwire.jlibtorrent.alerts.TrackerWarningAlert;
import com.frostwire.jlibtorrent.alerts.TrackeridAlert;
import com.frostwire.jlibtorrent.alerts.UnwantedBlockAlert;
import com.frostwire.jlibtorrent.alerts.UrlSeedAlert;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 * @author sv244
 */
public abstract class TorrentAlertListener implements AlertListener {

    private static final Logger LOG = Logger.getLogger(TorrentAlertListener.class);

    private static final Map<String, CallAlertFunction> CALL_TABLE = buildCallAlertTable();

    @Override
    public int[] types() {
        return null;
    }

    @Override
    public void alert(Alert<?> alert) {
        if (!(alert instanceof TorrentAlert<?>)) {
            return;
        }

        CallAlertFunction function = CALL_TABLE.get(alert.getClass().getName());
        if (function != null) {
            function.invoke(this, alert);
        }
    }

    public void torrentAdded(TorrentAddedAlert alert) {
    }

    public void torrentFinished(TorrentFinishedAlert alert) {
    }

    public void torrentRemoved(TorrentRemovedAlert alert) {
    }

    public void torrentUpdate(TorrentUpdateAlert alert) {
    }

    public void torrentDeleted(TorrentDeletedAlert alert) {
    }

    public void torrentPaused(TorrentPausedAlert alert) {
    }

    public void torrentResumed(TorrentResumedAlert alert) {
    }

    public void torrentChecked(TorrentCheckedAlert alert) {
    }

    public void torrentNeedCert(TorrentNeedCertAlert alert) {
    }

    public void torrentError(TorrentErrorAlert alert) {
    }

    public void addTorrent(AddTorrentAlert alert) {
    }

    public void blockFinished(BlockFinishedAlert alert) {
    }

    public void metadataReceived(MetadataReceivedAlert alert) {
    }

    public void metadataFailed(MetadataFailedAlert alert) {
    }

    public void saveResumeData(SaveResumeDataAlert alert) {
    }

    public void fastresumeRejected(FastresumeRejectedAlert alert) {
    }

    public void fileCompleted(FileCompletedAlert alert) {
    }

    public void fileRenamed(FileRenamedAlert alert) {
    }

    public void fileRenameFailed(FileRenameFailedAlert alert) {
    }

    public void fileError(FileErrorAlert alert) {
    }

    public void hashFailed(HashFailedAlert alert) {
    }

    public void trackerAnnounce(TrackerAnnounceAlert alert) {
    }

    public void trackerReply(TrackerReplyAlert alert) {
    }

    public void trackerWarning(TrackerWarningAlert alert) {
    }

    public void trackerError(TrackerErrorAlert alert) {
    }

    public void readPiece(ReadPieceAlert alert) {
    }

    public void stateChanged(StateChangedAlert alert) {
    }

    public void dhtReply(DhtReplyAlert alert) {
    }

    public void scrapeReply(ScrapeReplyAlert alert) {
    }

    public void scrapeFailed(ScrapeFailedAlert alert) {
    }

    public void lsdPeer(LsdPeerAlert alert) {
    }

    public void peerBlocked(PeerBlockedAlert alert) {
    }

    public void performance(PerformanceAlert alert) {
    }

    public void pieceFinished(PieceFinishedAlert alert) {
    }

    public void saveResumeDataFailed(SaveResumeDataFailedAlert alert) {
    }

    public void stats(StatsAlert alert) {
    }

    public void storageMoved(StorageMovedAlert alert) {
    }

    public void torrentDeleteFailed(TorrentDeleteFailedAlert alert) {
    }

    public void urlSeed(UrlSeedAlert alert) {
    }

    public void invalidRequest(InvalidRequestAlert alert) {
    }

    public void peerBan(PeerBanAlert alert) {
    }

    public void peerConnect(PeerConnectAlert alert) {
    }

    public void peerDisconnected(PeerDisconnectedAlert alert) {
    }

    public void peerError(PeerErrorAlert alert) {
    }

    public void peerSnubbed(PeerSnubbedAlert alert) {
    }

    public void peerUnsnubbe(PeerUnsnubbedAlert alert) {
    }

    public void requestDropped(RequestDroppedAlert alert) {
    }

    public void anonymousMode(AnonymousModeAlert alert) {
    }

    public void blockDownloading(BlockDownloadingAlert alert) {
    }

    public void blockTimeout(BlockTimeoutAlert alert) {
    }

    public void cacheFlushed(CacheFlushedAlert alert) {
    }

    public void storageMovedFailed(StorageMovedFailedAlert alert) {
    }

    public void trackerid(TrackeridAlert alert) {
    }

    public void unwantedBlock(UnwantedBlockAlert alert) {
    }

    public void torrentPrioritize(TorrentPrioritizeAlert alert) {
    }

    private static Map<String, CallAlertFunction> buildCallAlertTable() {
        Map<String, CallAlertFunction> map = new HashMap<String, CallAlertFunction>();

        for (Method m : TorrentAlertListener.class.getDeclaredMethods()) {
            Class<?> returnType = m.getReturnType();
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (isAlertMethod(returnType, parameterTypes)) {
                try {
                    Class<?> clazz = parameterTypes[0];
                    CallAlertFunction function = new CallAlertFunction(m);

                    map.put(clazz.getName(), function);
                } catch (Throwable e) {
                    LOG.warn(e.toString());
                }
            }
        }

        return Collections.unmodifiableMap(map);
    }

    private static boolean isAlertMethod(Class<?> returnType, Class<?>[] parameterTypes) {
        return returnType.equals(void.class) && parameterTypes.length == 1 && Alert.class.isAssignableFrom(parameterTypes[0]);
    }

    private static final class CallAlertFunction {

        private final Method method;

        public CallAlertFunction(Method method) {
            this.method = method;
        }

        public void invoke(TorrentAlertListener adapter, Alert<?> alert) {
            try {
                method.invoke(adapter, alert);
            } catch (Throwable e) {
                LOG.warn(e.toString());
            }
        }
    }
}