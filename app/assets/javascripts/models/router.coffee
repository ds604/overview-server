class Router
  constructor: (start_path=undefined) ->
    # TODO: test, etc
    @start_path = start_path || '' + window.document.location
    if match = /(\d+)[^\d]*$/.exec(@start_path)
      @document_set_id = +match[0]

  route_to_path: (route, id=undefined) ->
    switch (route)
      when 'root' then this._root_path()
      when 'node' then this._node_path(id)
      when 'documents' then this._documents_path()
      when 'document_view' then this._document_view_path(id)
      when 'create_log_entries' then this._create_log_entries_path()

  _root_path: () ->
    "/trees/#{@document_set_id}/root"

  _node_path: (id) ->
    "/trees/#{@document_set_id}/nodes/#{id}"

  _documents_path: () ->
    "/trees/#{@document_set_id}/documents"

  _document_view_path: (id) ->
    "/documents/#{id}"

  _create_log_entries_path: () ->
    "/documentsets/#{@document_set_id}/log-entries/create-many"

exports = require.make_export_object('models/router')
exports.Router = Router
