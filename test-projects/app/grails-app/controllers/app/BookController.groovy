package app

import grails.converters.JSON
import groovy.sql.Sql
import testapp.Book

import javax.sql.DataSource
import java.sql.Connection

/**
 * Created by sudhir on 31/10/17.
 */
class BookController {

	DataSource dataSource

	def index() {
		Book book = new Book(name: String.valueOf(System.currentTimeMillis()))
		book.save(flush:true)
		render book as JSON
	}
}
