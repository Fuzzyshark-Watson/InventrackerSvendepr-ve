package Fuzzcode.service;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.dao.ItemDao;
import Fuzzcode.model.Item;

import java.util.List;

public class ItemService {
    private final ItemDao itemDao = new ItemDao();

    public Item createItem(String tagId, Integer position, boolean overdue) {
        int id = itemDao.createItem(tagId, position, overdue);
        if (id == 0) {
            LoggerHandler.log(LoggerHandler.Level.ERROR, "Failed to create item: " + tagId);
            return null;
        }
        return itemDao.readItemById(id, false);
    }
    public Item getItemByTag(String tagId, boolean include) {
        return itemDao.readItemByTag(tagId, include);
    }
    public Item getItemById(int id, boolean include) {
        return itemDao.readItemById(id, include);
    }
    public boolean changeTag(String tagId, int itemId) {
        return itemDao.updateTag(tagId, itemId);
    }
    public boolean markOverdue(int itemId, boolean overdue) {
        return itemDao.updateOverdue(itemId, overdue);
    }
    public boolean moveItem(int itemId, int newPosition) {
        return itemDao.updatePosition(itemId, newPosition);
    }
    public boolean deleteItem(int itemId) {
        return itemDao.softDelete(itemId);
    }
    public List<Item> listActiveItems() {
        return itemDao.listAll(false);
    }
    public List<Item> listAllItems() {
        return itemDao.listAll(true);
    }
}
