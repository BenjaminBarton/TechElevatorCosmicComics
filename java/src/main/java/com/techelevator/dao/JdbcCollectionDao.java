package com.techelevator.dao;

import com.techelevator.model.Collection;
import com.techelevator.model.MarvelDataModels.MarvelItemNames;
import com.techelevator.model.MarvelDataModels.OverallMarvelResults;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JdbcCollectionDao implements CollectionDao {

    private final JdbcTemplate jdbcTemplate;
    private ComicDao comicDao;
    private UserDao userDao;

    public JdbcCollectionDao(JdbcTemplate jdbcTemplate, ComicDao comicDao, UserDao userDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.comicDao = comicDao;
        this.userDao = userDao;
    }

    @Override
    public Collection getCollectionById(int collectionId) {
        Collection collection = null;
        String sql = "SELECT * FROM collections WHERE collection_id = ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, collectionId);
        if (results.next()) {
            collection = mapRowToCollection(results);
        }
        return collection;
    }

    @Override
    public List<Collection> getCollections() {
        return getCollections("SELECT * FROM collections", new Object[] {}, new int[] {});
    }

    @Override
    public List<Collection> getCollections(String sql, Object[] args, int[] types) {
        List<Collection> collections = new ArrayList<>();
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, args, types);
        while (results.next()) {
            collections.add(mapRowToCollection(results));
        }
        return collections;
    }

    @Override
    public List<Collection> getCollections(String name, int limit, int page, int userId) {
        List<Collection> collections = new ArrayList<>();
        String sql = "SELECT * FROM collections AS c WHERE c.collection_name ILIKE ? AND (c.user_id = ?) GROUP BY " +
                "c.collection_id ORDER BY c.collection_id ASC LIMIT ? OFFSET ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, "%" + name + "%", userId, limit, page*limit);
        while (results.next()) {
            collections.add(mapRowToCollection(results));
        }
        return collections;
    }

    @Override
    public List<Collection> getCollections(String name, int limit, int page) {
        List<Collection> collections = new ArrayList<>();
        String sql = "SELECT * FROM collections AS c WHERE c.collection_name ILIKE ? GROUP BY c.collection_id ASC LIMIT ? OFFSET ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, "%" + name + "%", limit, page*limit);
        while (results.next()) {
            collections.add(mapRowToCollection(results));
        }
        return collections;
    }

    @Override
    public List<Collection> getCollectionsByUser(int userId) {
        return getCollections("SELECT * FROM collections;", new Object[] {userId}, new int[] {java.sql.Types.INTEGER});
    }

    @Override
    public void addCollection(Collection newCollection) {
        String sql = "INSERT INTO collections (user_id, collection_name) VALUES (?, ?);";
        jdbcTemplate.update(sql, newCollection.getUserId(), newCollection.getName());
    }

    @Override
    public void removeCollection(int collectionId) {
        jdbcTemplate.update("DELETE FROM comics_collections WHERE collection_id = ?;", collectionId);
        jdbcTemplate.update("DELETE FROM collections WHERE collection_id = ?;", collectionId);
    }

    @Override
    public void addComic(OverallMarvelResults newComic, int collectionId) {
        String sql = "INSERT INTO comics (comic_id, title, thumbnail_url) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;";
        jdbcTemplate.update(sql, newComic.getId(), newComic.getTitle(), newComic.getThumbnail().getPath() + "." + newComic.getThumbnail().getExtension());
        sql = "INSERT INTO comics_collections (comic_id, collection_id) VALUES (?, ?) ON CONFLICT DO NOTHING;";
        jdbcTemplate.update(sql, newComic.getId(), collectionId);
        for (MarvelItemNames creator : newComic.getCreators().getItems()) {
            if (creator.getResourceURI() != null) {
                int id = 0;
                try {
                    id = Integer.parseInt(creator.getResourceURI().substring(creator.getResourceURI().lastIndexOf('/') + 1));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                sql = "INSERT INTO creators (creator_id, first_name, last_name, thumbnail_url) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING;";
                String firstName = "";
                String lastName = "";
                try {
                    firstName = creator.getName().substring(0, creator.getName().indexOf(' '));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                try {
                    lastName = creator.getName().substring(creator.getName().lastIndexOf(' '));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                jdbcTemplate.update(sql, id, firstName, lastName, "");
                if (!comicDao.hasAuthor(newComic.getId(), id)) {
                    sql = "INSERT INTO comics_creators (comic_id, creator_id) VALUES (?, ?) ON CONFLICT DO NOTHING;";
                    jdbcTemplate.update(sql, newComic.getId(), id);
                }
            }
        }
    }

    @Override
    public void deleteComic(int collectionId, int comicId) {
        String sql = "DELETE FROM comics_collections WHERE comic_id = ? AND collection_id = ?;";
        jdbcTemplate.update(sql, comicId, collectionId);
    }

    @Override
    public int getCollectionOwnerId(int collectionId) {
        String sql = "SELECT user_id FROM collections WHERE collection_id = ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, collectionId);
        if (results.next()) {
            return results.getInt("user_id");
        }
        return -1;
    }

    @Override
    public List<Collection> getCollectionsByUserId(int userId) {
        List<Collection> collections = new ArrayList<>();
        String sql = "SELECT * FROM collections WHERE user_id = ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, userId);
        while (results.next()) {
            collections.add(mapRowToCollection(results));
        }
        return collections;
    }

    @Override
    public int getCollectionOwner(int collectionId) {
        String sql = "SELECT user_id FROM collections WHERE collection_id = ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, collectionId);
        if (results.next()) {
            return results.getInt("user_id");
        }
        return 0;
    }

    @Override
    public String getThumbnail(int collectionId) {
        String sql = "SELECT c.thumbnail_url FROM comics AS c INNER JOIN comics_collections AS cc ON c.comic_id = cc.comic_id WHERE collection_id = ?;";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, collectionId);
        if (results.next()) {
            return results.getString("thumbnail_url");
        }
        return "";
    }

    private Collection mapRowToCollection(SqlRowSet rowSet) {
        Collection collection = new Collection();
        collection.setId(rowSet.getInt("collection_id"));
        collection.setComics(comicDao.getComicsByCollectionId(rowSet.getInt("collection_id")));
        collection.setUserId(rowSet.getInt("user_id"));
        collection.setName(rowSet.getString("collection_name"));
        return collection;
    }
}
