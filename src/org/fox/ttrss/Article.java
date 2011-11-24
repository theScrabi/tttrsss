package org.fox.ttrss;
import java.util.List;


public class Article {
	int id;
	boolean unread; 
	boolean marked; 
	boolean published; 
	int updated; 
	boolean is_updated; 
	String title; 
	String link; 
	int feed_id; 
	List<String> tags; 
	String content;
	boolean _selected;
}
