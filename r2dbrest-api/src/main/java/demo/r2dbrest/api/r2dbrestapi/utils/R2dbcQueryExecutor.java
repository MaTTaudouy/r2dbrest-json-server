package demo.r2dbrest.api.r2dbrestapi.utils;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcQueryExecutor {

	private static final String EXISTS_TABLE_FOR_NAME = "SELECT to_regclass('public.%s')::text;";

	private static final String CREATE_TABLE_FOR_NAME = "CREATE TABLE \"%s\" (\"id\" uuid NOT NULL,\"data\" json NOT NULL);";

	private R2dbcEntityTemplate r2dbcTemplate;

	private R2dbcJsonConverter r2dbcJsonConverter;

	private boolean forceIdInUpdate = false;

	public R2dbcQueryExecutor(R2dbcEntityTemplate r2dbcTemplate, R2dbcJsonConverter r2dbcJsonConverter) {
		this.r2dbcTemplate = r2dbcTemplate;
		this.r2dbcJsonConverter = r2dbcJsonConverter;
	}

	public Flux<Map<String, Object>> findAll(String tablename) {
		return r2dbcTemplate.getDatabaseClient().select().from(tablename)
				.project(r2dbcJsonConverter.getJsonColumnname()).fetch().all().map((v) -> {
					return r2dbcJsonConverter.convertColumn(v);
				});
	}

	public Mono<Map<String, Object>> save(String tableName, Mono<Map<String, Object>> jsonData) {
		return jsonData.flatMap((data) -> this.insertData(UUID.randomUUID(), tableName, data));
	}

	private Mono<Map<String, Object>> insertData(UUID id, String tableName, Map<String, Object> jsonData) {
		// Add id into Json column or replace it with the generate value
		jsonData.put("id", id);
		return r2dbcTemplate.getDatabaseClient().insert().into(tableName)
				.value(r2dbcJsonConverter.getJsonColumnname(), jsonData).value("id", id).fetch().one()
				.map(r2dbcJsonConverter::convertColumn);
	}

	public Mono<Map<String, Object>> createTableIfNecessary(String tablename) {
		return r2dbcTemplate.getDatabaseClient().execute(String.format(EXISTS_TABLE_FOR_NAME, tablename)).fetch().one()
				.filter((v) -> Objects.nonNull(v.get("to_regclass"))).switchIfEmpty(createTable(tablename));
	}

	private Mono<Map<String, Object>> createTable(String tablename) {
		return r2dbcTemplate.getDatabaseClient().execute(String.format(CREATE_TABLE_FOR_NAME, tablename)).fetch().one();
	}

	public Mono<Map<String, Object>> findById(String tablename, String id) {
		return r2dbcTemplate.getDatabaseClient().select().from(tablename).matching(whereUUIDCriteria(id))
				.project(r2dbcJsonConverter.getJsonColumnname()).fetch().one().map(r2dbcJsonConverter::convertColumn);
	}

	private Criteria whereUUIDCriteria(String id) {
		return Criteria.where("id").is(UUID.fromString(id));
	}

	public Mono<Integer> update(String tablename, String id, Mono<Map<String, Object>> jsonData) {
		return jsonData.flatMap((data) -> this.updateData(id, tablename, data));
	}

	private Mono<Integer> updateData(String id, String tablename, Map<String, Object> jsonData) {
		if (forceIdInUpdate) {
			// Update id into Json column with the good value to prevent integrity issues
			jsonData.put("id", id);
		}
		return r2dbcTemplate.getDatabaseClient().update().table(tablename)
				.using(Update.update(r2dbcJsonConverter.getJsonColumnname(), jsonData)).matching(whereUUIDCriteria(id))
				.fetch().rowsUpdated();
	}

}
