package com.matburt.mobileorg2.OrgData;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import com.matburt.mobileorg2.OrgData.OrgContract.Edits;
import com.matburt.mobileorg2.OrgData.OrgContract.Files;
import com.matburt.mobileorg2.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg2.OrgData.OrgContract.Priorities;
import com.matburt.mobileorg2.OrgData.OrgContract.Tags;
import com.matburt.mobileorg2.OrgData.OrgContract.Todos;
import com.matburt.mobileorg2.util.FileUtils;
import com.matburt.mobileorg2.util.OrgFileNotFoundException;
import com.matburt.mobileorg2.util.OrgNodeNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrgProviderUtils {
	
	public static HashMap<String, String> getFileChecksums(ContentResolver resolver) {
		HashMap<String, String> checksums = new HashMap<String, String>();

		Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
				null, null, null);
		cursor.moveToFirst();

		while (cursor.isAfterLast() == false) {
			OrgFile orgFile = new OrgFile();
			
			try {
				orgFile.set(cursor);
				checksums.put(orgFile.filename, orgFile.checksum);
			} catch (OrgFileNotFoundException e) {}
			cursor.moveToNext();
		}

		cursor.close();
		return checksums;
	}

	/**
	 *
	 * @param context
	 * @return the list of nodes corresponding to a file
	 */
	public static List<OrgNode> getFileNodes(Context context){
        return OrgProviderUtils.getOrgNodeChildren(-1, context.getContentResolver());
    }

	public static ArrayList<String> getFilenames(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();

		Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
				null, null, Files.DEFAULT_SORT);
		cursor.moveToFirst();

		while (!cursor.isAfterLast()) {
			OrgFile orgFile = new OrgFile();
			
			try {
				orgFile.set(cursor);
				result.add(orgFile.filename);
			} catch (OrgFileNotFoundException e) {}
			cursor.moveToNext();
		}

		cursor.close();
		return result;
	}
	
	public static ArrayList<String> getFileAliases(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();

		Cursor cursor = resolver.query(Files.CONTENT_URI, Files.DEFAULT_COLUMNS,
				null, null, Files.DEFAULT_SORT);
		cursor.moveToFirst();

		while (cursor.isAfterLast() == false) {
			OrgFile orgFile = new OrgFile();
			
			try {
				orgFile.set(cursor);
				result.add(orgFile.name);
			} catch (OrgFileNotFoundException e) {}
			
			cursor.moveToNext();
		}

		cursor.close();
		return result;
	}

	public static void addTodos(HashMap<String, Boolean> todos,
								ContentResolver resolver) {
		if(todos == null) return;
		for (String name : todos.keySet()) {
			ContentValues values = new ContentValues();
			values.put(Todos.NAME, name);
			values.put(Todos.GROUP, 0);

			if (todos.get(name))
				values.put(Todos.ISDONE, 1);

			try{
				resolver.insert(Todos.CONTENT_URI, values);
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	public static ArrayList<String> getTodos(ContentResolver resolver) {
		Cursor cursor = resolver.query(Todos.CONTENT_URI, new String[] { Todos.NAME }, null, null, Todos.ID);
		if(cursor==null) return new ArrayList<>();

		ArrayList<String> todos = cursorToArrayList(cursor);

		return todos;
	}
	
	public static void setPriorities(ArrayList<String> priorities, ContentResolver resolver) {
		resolver.delete(Priorities.CONTENT_URI, null, null);

		for (String priority : priorities) {
			ContentValues values = new ContentValues();
			values.put(Priorities.NAME, priority);
			resolver.insert(Priorities.CONTENT_URI, values);
		}
	}

	public static ArrayList<String> getPriorities(ContentResolver resolver) {
		Cursor cursor = resolver.query(Priorities.CONTENT_URI, new String[] { Priorities.NAME },
				null, null, Priorities.ID);
		ArrayList<String> priorities = cursorToArrayList(cursor);

		cursor.close();
		return priorities;
	}
	

	public static void setTags(ArrayList<String> priorities, ContentResolver resolver) {
		resolver.delete(Tags.CONTENT_URI, null, null);

		for (String priority : priorities) {
			ContentValues values = new ContentValues();
			values.put(Tags.NAME, priority);
			resolver.insert(Tags.CONTENT_URI, values);
		}
	}
	public static ArrayList<String> getTags(ContentResolver resolver) {
		Cursor cursor = resolver.query(Tags.CONTENT_URI, new String[] { Tags.NAME },
				null, null, Tags.ID);
		ArrayList<String> tags = cursorToArrayList(cursor);

		cursor.close();
		return tags;
	}

	public static ArrayList<String> cursorToArrayList(Cursor cursor) {
		ArrayList<String> list = new ArrayList<>();
		if(cursor == null) return list;
		cursor.moveToFirst();

		while (!cursor.isAfterLast()) {
			list.add(cursor.getString(0));
			cursor.moveToNext();
		}
		return list;
	}

	public static ArrayList<OrgNode> getOrgNodePathFromTopLevel(long node_id, ContentResolver resolver) {
		ArrayList<OrgNode> nodes = new ArrayList<OrgNode>();
		
		long currentId = node_id;
		while(currentId >= 0) {
			try {
				OrgNode node = new OrgNode(currentId, resolver);
				nodes.add(node);
				currentId = node.parentId;
			} catch (OrgNodeNotFoundException e) {
				throw new IllegalStateException("Couldn't build entire path to root from a given node");
			}
		}
		
		Collections.reverse(nodes);
		return nodes;
	}
	
	public static OrgNode getOrgNodeFromOlpPath(String olpPath, ContentResolver resolver) throws OrgNodeNotFoundException, OrgFileNotFoundException {
		if(olpPath == null || olpPath.equals(""))
			throw new IllegalArgumentException("Empty Olp path received");
		
		Matcher matcher = Pattern.compile("olp:([^:]+):?" + "(.*)").matcher(olpPath);
		
		String filename;
		String[] nodes = new String[0];
		if(matcher.find()) {
			filename = matcher.group(1);
			
			if(matcher.group(2) != null && matcher.group(2).trim().equals("") == false) {
				nodes = matcher.group(2).split("/");
			}
		} else
			throw new IllegalArgumentException("Olp path " + olpPath + " is not valid");

		OrgNode node = new OrgFile(filename, resolver).getOrgNode(resolver);
		
		for(String nodeName: nodes)
			node = node.getChild(nodeName, resolver);
		
		return node;
	}
	
	public static StringBuilder nodesToString(long node_id, long level, ContentResolver resolver) {
		StringBuilder result = new StringBuilder();
		
		try {
			OrgNode node = new OrgNode(node_id, resolver);
			
			if(level != 0) // Don't add top level file node heading
				result.append(node.toString() + "\n");
			
			for (OrgNode child : node.getChildren(resolver))
				result.append(nodesToString(child.id, level + 1, resolver));
			
		} catch (OrgNodeNotFoundException e) {}

		return result;
	}
	
	public static void clearDB(ContentResolver resolver) {
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
		resolver.delete(Edits.CONTENT_URI, null, null);
	}
	
	
	public static OrgNode getOrgNodeFromFilename(String filename, ContentResolver resolver) throws OrgFileNotFoundException {
		OrgFile file = new OrgFile(filename, resolver);
		try {
			return new OrgNode(file.nodeId, resolver);
		} catch (OrgNodeNotFoundException e) {
			throw new IllegalStateException("OrgNode for file " + file.name
					+ " should exist");
		}
	}
	
	public static OrgFile getOrCreateCaptureFile (ContentResolver resolver) {
		return getOrCreateFile(FileUtils.CAPTURE_FILE, FileUtils.CAPTURE_FILE_ALIAS, resolver);
	}
	
	public static OrgFile getOrCreateFile(String filename, String fileAlias, ContentResolver resolver) {
		OrgFile file = new OrgFile(filename, fileAlias, "");
		if(file.doesFileExist(resolver) == false) {
			file.includeInOutline = true;
			file.write(resolver);
		} else {
			try {
			file = new OrgFile(filename, resolver);
			} catch (OrgFileNotFoundException e) {}
		}
		return file;
	}
	
	public static OrgNode getOrgNodeFromFileAlias(String fileAlias, ContentResolver resolver) throws OrgNodeNotFoundException {
		Cursor cursor = resolver.query(OrgData.CONTENT_URI,
				OrgData.DEFAULT_COLUMNS, OrgData.NAME + "=? AND " + OrgData.PARENT_ID + "=-1", new String[] {fileAlias}, null);
		OrgNode node = new OrgNode();
		node.set(cursor);
		cursor.close();
		return node;
	}
	
	public static OrgFile getOrCreateFileFromAlias(String fileAlias, ContentResolver resolver) {
		Cursor cursor = resolver.query(Files.CONTENT_URI,
				Files.DEFAULT_COLUMNS, Files.NAME + "=?", new String[] {fileAlias}, null);
		if(cursor == null || cursor.getCount() == 0) {
			if(fileAlias.equals(OrgFile.CAPTURE_FILE_ALIAS))
				return getOrCreateCaptureFile(resolver);
			else
				return getOrCreateFile(fileAlias, fileAlias, resolver);
		} else {
			OrgFile file = new OrgFile();
			try {
				file.set(cursor);
			} catch (OrgFileNotFoundException e) {}
			cursor.close();
			return file;
		}
	}
	
	public static ArrayList<String> getActiveTodos(ContentResolver resolver) {
		ArrayList<String> result = new ArrayList<String>();

		Cursor cursor = resolver.query(Todos.CONTENT_URI,
				Todos.DEFAULT_COLUMNS, null, null, null);
		if(cursor == null)
			return result;
		
		cursor.moveToFirst();
		
		while (cursor.isAfterLast() == false) {
			int isdone = cursor.getInt(cursor.getColumnIndex(Todos.ISDONE));

			if (isdone == 0)
				result.add(cursor.getString(cursor.getColumnIndex(Todos.NAME)));
			cursor.moveToNext();
		}
		cursor.close();
		return result;
	}
	
	public static boolean isTodoActive(String todo, ContentResolver resolver) {
		if(TextUtils.isEmpty(todo))
			return true;

		Cursor cursor = resolver.query(Todos.CONTENT_URI, Todos.DEFAULT_COLUMNS, Todos.NAME + " = ?",
				new String[] { todo }, null);		
		
		if(cursor.getCount() > 0) {
			cursor.moveToFirst();
			int isdone = cursor.getInt(cursor.getColumnIndex(Todos.ISDONE));
			cursor.close();

			return isdone == 0;
		}

		if(cursor!=null) cursor.close();

		return false;
	}
	
	public static Cursor getFileSchedule(String filename, boolean showHabits, ContentResolver resolver) throws OrgFileNotFoundException {
		OrgFile file = new OrgFile(filename, resolver);
		
		String whereQuery = OrgData.FILE_ID + "=? AND (" + OrgData.PAYLOAD + " LIKE '%<%>%'";

		if(showHabits == false)
			whereQuery += " AND NOT " + OrgData.PAYLOAD + " LIKE '%:STYLE: habit%'";
		
		whereQuery += ")";
			
		Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, whereQuery,
				new String[] { Long.toString(file.id) }, null);
		cursor.moveToFirst();
		return cursor;
	}
	
	public static Cursor search(String query, ContentResolver resolver) {
		Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
				OrgData.NAME + " LIKE ?", new String[] { query },
				OrgData.DEFAULT_SORT);
		
		return cursor;
	}
	
	public static int getChangesCount(ContentResolver resolver) {
		int changes = 0;
		Cursor cursor = resolver.query(Edits.CONTENT_URI,
				Edits.DEFAULT_COLUMNS, null, null, null);
		if(cursor != null) {
			changes += cursor.getCount();
			cursor.close();
		}
		
		long file_id = -2;
		try {
			file_id = new OrgFile(FileUtils.CAPTURE_FILE, resolver).nodeId;
		} catch (OrgFileNotFoundException e) {}
		cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.FILE_ID + "=?",
				new String[] { Long.toString(file_id) }, null);
		if(cursor != null) {
			int captures = cursor.getCount();
			if(captures > 0)
				changes += captures;
			cursor.close();
		}
		
		return changes;
	}

	public static String getChangesString(ContentResolver content) {
		int changes = OrgProviderUtils.getChangesCount(content);
		if(changes > 0)
			return "[" + changes + "]";
		else
			return "";
	}

	public static ArrayList<OrgNode> getOrgNodeChildren(long nodeId, ContentResolver resolver) {
		
		String sort = nodeId == -1 ? OrgData.NAME_SORT : OrgData.POSITION_SORT;
		Log.v("sort", "sort : " + sort);
		Cursor childCursor = resolver.query(OrgData.buildChildrenUri(nodeId),
				OrgData.DEFAULT_COLUMNS, null, null, sort);

		if(childCursor!=null) {
			ArrayList<OrgNode> result = orgDataCursorToArrayList(childCursor);
			childCursor.close();
			return result;
		}else{
			return new ArrayList<OrgNode>();
		}
	}
	
	public static ArrayList<OrgNode> orgDataCursorToArrayList(Cursor cursor) {
		ArrayList<OrgNode> result = new ArrayList<OrgNode>();

		cursor.moveToFirst();
		
		while(cursor.isAfterLast() == false) {
			try {
				result.add(new OrgNode(cursor));
			} catch (OrgNodeNotFoundException e) {}
			cursor.moveToNext();
		}
		
		return result;
	}
}