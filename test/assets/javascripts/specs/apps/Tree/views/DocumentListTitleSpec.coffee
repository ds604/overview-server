define [
  'underscore'
  'apps/Tree/views/DocumentListTitle'
  'apps/Tree/models/observable'
  'i18n'
], (_, DocumentListTitle, observable, i18n) ->
  class TagStore
    observable(this)

    find_by_id: (id) -> { id: id, name: "Tag #{id}" }

  class SearchResultStore
    observable(this)

    find_by_id: (id) -> { id: id, query: "Search #{id}" }

  class OnDemandTree
    constructor: ->
      @nodes =
        '1': { id: 1, description: 'Node 1' }
        '2': { id: 2, description: 'Node 2' }

  cache =
    tag_store: new TagStore()
    on_demand_tree: new OnDemandTree()
    search_result_store: new SearchResultStore()

  class DocumentList
    observable(this)

    constructor: (@selection, @n) ->
      @cache = cache

  describe 'apps/Tree/views/DocumentListTitleView', ->
    documentList = undefined
    view = undefined

    beforeEach ->
      i18n.reset_messages({
        'views.DocumentSet.show.DocumentListTitle.num_documents': 'num_documents,{0}'
        'views.DocumentSet.show.DocumentListTitle.loading': 'loading'
        'views.DocumentSet.show.DocumentListTitle.tag.title_html': 'tag.title_html,{0},{1}'
        'views.DocumentSet.show.DocumentListTitle.tag.edit': 'tag.edit'
        'views.DocumentSet.show.DocumentListTitle.untagged.title_html': 'untagged.title_html,{0}'
        'views.DocumentSet.show.DocumentListTitle.node.title_html': 'node.title_html,{0},{1}'
        'views.DocumentSet.show.DocumentListTitle.node.edit': 'node.edit'
        'views.DocumentSet.show.DocumentListTitle.searchResult.title_html': 'searchResult.title_html,{0},{1}'
        'views.DocumentSet.show.DocumentListTitle.searchResult.edit': 'searchResult.edit'
        'views.DocumentSet.show.DocumentListTitle.multiple.title_html': 'multiple.title_html,{0}'
      })

    afterEach ->
      view?.off()
      view?.remove()

    describe 'with an empty selection', ->
      beforeEach ->
        documentList = undefined
        view = new DocumentListTitle({ documentList: undefined, cache: cache })

      it 'should render nothing', ->
        expect(view.$el.html()).toEqual('')

    describe 'with an unloaded documentList', ->
      beforeEach ->
        documentList = new DocumentList({ nodes: [], tags: [1], searchResults: [] }, undefined)
        view = new DocumentListTitle({ documentList: documentList, cache: cache })

      it 'should render loading message', ->
        expect(view.$el.text()).toMatch(/loading/)

    describe 'with a Tag', ->
      beforeEach ->
        documentList = new DocumentList({ nodes: [], tags: [1], searchResults: [] }, 4)
        view = new DocumentListTitle({ documentList: documentList, cache: cache })

      it 'should render the title', ->
        expect(view.$('h4').text()).toEqual('tag.title_html,num_documents,4,Tag 1')

      it 'should trigger edit-tag', ->
        args = []
        view.on('edit-tag', -> args = _.toArray(arguments))
        view.$('a.tag-edit').click()
        expect(args).toEqual([ 1 ])

      it 'should trigger edit-tag correctly after id-changed', ->
        args = []
        view.on('edit-tag', -> args = _.toArray(arguments))
        cache.tag_store._notify('id-changed', 1, { id: 2, name: 'Tag 1' })
        view.$('a.tag-edit').click()
        expect(args).toEqual([ 2 ])

      it 'should adjust title after changed', ->
        # Hack-ish test setup
        cache.tag_store.find_by_id = (id) -> { id: id, name: "Tag 2" }
        cache.tag_store._notify('changed', { id: 1, name: 'Tag 2' })
        # But this line is nice
        expect(view.$('h4').text()).toEqual('tag.title_html,num_documents,4,Tag 2')

      it 'should render when calling setDocumentList()', ->
        documentList2 = new DocumentList({ nodes: [], tags: [2], searchResults: [] }, 8)
        view.setDocumentList(documentList2)
        expect(view.$('h4').text()).toEqual('tag.title_html,num_documents,8,Tag 2')

    describe 'with the untagged tag', ->
      beforeEach ->
        spyOn(cache.tag_store, 'find_by_id')  # untagged isn't present in the tag store

        documentList = new DocumentList({ nodes: [], tags: [0], searchResults: [] }, 4)
        view = new DocumentListTitle({ documentList: documentList, cache: cache })

      it 'should render the title', ->
        expect(cache.tag_store.find_by_id).not.toHaveBeenCalled()
        expect(view.$('h4').text()).toEqual('untagged.title_html,num_documents,4')

    describe 'with a Node', ->
      beforeEach ->
        documentList = new DocumentList({ nodes: [1], tags: [], searchResults: [] }, 4)
        view = new DocumentListTitle({ documentList: documentList, cache: cache })

      it 'should render the title', ->
        expect(view.$('h4').text()).toEqual('node.title_html,num_documents,4,Node 1')

      it 'should trigger edit-tag', ->
        args = []
        view.on('edit-node', -> args = _.toArray(arguments))
        view.$('a.node-edit').click()
        expect(args).toEqual([ 1 ])

    describe 'with a SearchResult', ->
      beforeEach ->
        documentList = new DocumentList({ nodes: [], tags: [], searchResults: [1] }, 4)
        view = new DocumentListTitle({ documentList: documentList, cache: cache })

      it 'should render the title', ->
        expect(view.$('h4').text()).toEqual('searchResult.title_html,num_documents,4,Search 1')

    describe 'with Tags and/or Nodes', ->
      beforeEach ->
        documentList = new DocumentList({ nodes: [1], tags: [1], searchResults: [] }, 4)
        view = new DocumentListTitle({ documentList: documentList, cache: cache })

      it 'should render the title', ->
        expect(view.$('h4').text()).toEqual('multiple.title_html,num_documents,4')
