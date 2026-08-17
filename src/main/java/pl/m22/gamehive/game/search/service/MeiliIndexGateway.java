package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.exceptions.MeilisearchTimeoutException;
import com.meilisearch.sdk.json.JsonHandler;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import java.time.Duration;
import java.util.List;

@Slf4j
public class MeiliIndexGateway {

    private static final String PRIMARY_KEY = "id";
    private static final String INDEX_ALREADY_EXISTS = "index_already_exists";
    private static final int TASK_POLL_INTERVAL_MILLIS = 50;

    private final Client client;
    private final JsonHandler jsonHandler;
    private final String indexUid;
    private final int taskWaitTimeoutMillis;

    public MeiliIndexGateway(Client client, JsonHandler jsonHandler, String indexUid, Duration taskWaitTimeout) {

        this.client = client;
        this.jsonHandler = jsonHandler;
        this.indexUid = indexUid;
        this.taskWaitTimeoutMillis = Math.toIntExact(taskWaitTimeout.toMillis());
    }

    public String indexUid() {

        return indexUid;
    }

    public TaskInfo addDocuments(List<?> documents, String action) {

        return call(() -> index().addDocuments(jsonHandler.encode(documents), PRIMARY_KEY), action);
    }

    public TaskInfo deleteDocument(String documentId, String action) {

        return call(() -> index().deleteDocument(documentId), action);
    }

    public TaskInfo deleteAllDocuments(String action) {

        return call(() -> index().deleteAllDocuments(), action);
    }

    public SearchResultPaginated searchPaginated(SearchRequest request, String action) {

        return call(() -> (SearchResultPaginated) index().search(request), action);
    }

    public void ensureIndexSettings(String[] searchableAttributes, String[] filterableAttributes) {

        call(() -> {
            createIndexIfMissing();
            index().updateSearchableAttributesSettings(searchableAttributes);
            return index().updateFilterableAttributesSettings(filterableAttributes);
        }, "configure index " + indexUid);
    }

    public boolean awaitTaskSucceeded(TaskInfo taskInfo, String action) {

        int taskUid = taskInfo.getTaskUid();

        if (!waitUntilSettled(taskUid, action)) {
            return false;
        }

        Task task = call(() -> index().getTask(taskUid), action);

        if (task.getStatus() != TaskStatus.SUCCEEDED) {
            log.error("Meili task {} ({}) finished with status {}: {}", taskUid, action, task.getStatus(),
                    task.getError() != null ? task.getError().getMessage() : "no error details");

            return false;
        }
        log.debug("Meili task {} ({}) succeeded", taskUid, action);

        return true;
    }

    public void awaitTaskOrThrow(TaskInfo taskInfo, String action) {

        if (!awaitTaskSucceeded(taskInfo, action)) {
            throw new InfrastructureException(ErrorCode.SEARCH_FAILED);
        }
    }

    private boolean waitUntilSettled(int taskUid, String action) {

        try {
            index().waitForTask(taskUid, taskWaitTimeoutMillis, TASK_POLL_INTERVAL_MILLIS);

            return true;
        } catch (MeilisearchTimeoutException _) {
            log.warn("Meili task {} ({}) did not settle within {} ms - status unknown, it may still complete",
                    taskUid, action, taskWaitTimeoutMillis);

            return false;
        } catch (MeilisearchException e) {
            throw failure(e, action);
        }
    }

    private void createIndexIfMissing() {

        try {
            client.createIndex(indexUid, PRIMARY_KEY);
        } catch (MeilisearchApiException e) {
            if (!INDEX_ALREADY_EXISTS.equals(e.getCode())) {
                throw e;
            }
            log.debug("Meili index {} already exists", indexUid);
        }
    }

    private Index index() {

        return client.index(indexUid);
    }

    private <T> T call(MeiliCall<T> meiliCall, String action) {

        try {
            return meiliCall.execute();
        } catch (MeilisearchException e) {
            throw failure(e, action);
        }
    }

    private InfrastructureException failure(MeilisearchException e, String action) {

        if (e instanceof MeilisearchCommunicationException || e instanceof MeilisearchTimeoutException) {
            log.error("Meilisearch unreachable while trying to {}", action, e);

            return new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE);
        }
        log.error("Meilisearch rejected request while trying to {}", action, e);

        return new InfrastructureException(ErrorCode.SEARCH_FAILED);
    }

    @FunctionalInterface
    private interface MeiliCall<T> {

        T execute() throws MeilisearchException;
    }
}
