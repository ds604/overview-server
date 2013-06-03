define [
  'jquery'
  '../models/observable'
], ($, observable) ->
  TAG_ID_KEY = 'overview-tag-id'

  class TagListView
    observable(this)

    constructor: (@div, @tag_store, @state) ->
      this._init_html()
      this._observe_tag_add()
      this._observe_tag_remove()
      this._observe_tag_change()
      this._observe_tag_id_change()
      this._observe_shown_tag()
      this._observe_selected_tags()

    _init_html: () ->
      $div = $(@div)
      $ul = $('<ul class="btn-toolbar"></ul>')
      $form_li = $('<li class="btn-group"></li>')
      $form_li.append(this._create_form())
      $ul.append($form_li)
      $div.append($ul)

      notify = this._notify.bind(this)

      element_to_tag = (elem) =>
        $li = $(elem).closest('li')
        tagid = +$li.attr("data-#{TAG_ID_KEY}")
        tag = @tag_store.find_by_id(tagid)

      $ul.on 'click', 'a.tag-name', (e) ->
        e.preventDefault()
        notify('tag-clicked', element_to_tag(this))

      $ul.on 'click', 'a.tag-add', (e) ->
        e.preventDefault()
        notify('add-clicked', element_to_tag(this))

      $ul.on 'click', 'a.tag-remove', (e) ->
        e.preventDefault()
        notify('remove-clicked', element_to_tag(this))

      $li = $('<li class="btn-group"><a class="btn organize" href="#">organize tags…</a></li>')
      $li.on 'click', (e) =>
        e.preventDefault()
        notify('organize-clicked')
      $ul.append($li)

      this._refresh_shown_tag()
      this._refresh_selected_tags()

      undefined

    _create_form: () ->
      $form = $('<form method="post" action="#" class="input-append"><input type="text" name="tag_name" placeholder="tag name" class="input-mini" /><input type="submit" value="Create new tag" class="btn" /></form>')
      $form.on 'submit', (e) =>
        e.preventDefault()
        $input = $form.find('input[name=tag_name]')
        name = $.trim($input.val())
        if name.length > 0
          this._create_or_add_tag(name)
        $input.val('')

    _observe_tag_add: () ->
      @tag_store.observe 'added', (obj) =>
        this._add_tag(obj)

    _create_or_add_tag: (name) ->
      tag = @tag_store.find_by_name(name)
      if tag?
        this._notify('add-clicked', tag)
      else
        this._notify('create-submitted', { name: name })

    _add_tag: (tag) ->
      $li = $('<li class="btn-group"><a class="btn tag-name"></a><a class="btn tag-add" alt="add tag to selection" title="add tag to selection"><i class="icon-plus"></i></a><a class="btn tag-remove" alt="remove tag from selection" title="remove tag from selection"><i class="icon-minus"></i></a></li>')
      $li.attr("data-#{TAG_ID_KEY}", tag.id)
      $li.find('.tag-name').text(tag.name)
      $li.css('background-color', tag.color)

      $ul = $('ul', @div)
      $li.insertBefore($ul.children()[tag.position])

    _observe_tag_remove: () ->
      @tag_store.observe 'removed', (tag) =>
        this._remove_tag(tag)

    _observe_tag_id_change: () ->
      @tag_store.observe 'id-changed', (old_tagid, tag) =>
        $("li[data-#{TAG_ID_KEY}=#{old_tagid}]", @div).attr("data-#{TAG_ID_KEY}", tag.id)

    _observe_tag_change: () ->
      @tag_store.observe 'changed', (tag) =>
        this._change_tag(tag)

    _remove_tag: (tag) ->
      $li = $("li[data-#{TAG_ID_KEY}=#{tag.id}]", @div)
      $li.remove()

    _change_tag: (tag) ->
      $li = $("li[data-#{TAG_ID_KEY}=#{tag.id}]", @div)
      $li.css('background-color', tag.color)
      $li.find('.tag-name').text(tag.name)

      $ul = $li.parent()
      $li.remove()
      $li.insertBefore($ul.children()[tag.position])

    _observe_shown_tag: () ->
      @state.observe('focused_tag-changed', this._refresh_shown_tag.bind(this))

    _refresh_shown_tag: () ->
      $div = $(@div)
      $div.find('.shown').removeClass('shown')
      if @state.focused_tag?
        $div.find("[data-#{TAG_ID_KEY}=#{@state.focused_tag.id}]").addClass('shown')

    _observe_selected_tags: () ->
      @state.observe('selection-changed', this._refresh_selected_tags.bind(this))

    _refresh_selected_tags: () ->
      $div = $(@div)
      $div.find('.selected').removeClass('selected')
      for tagid in @state.selection.tags
        $div.find("[data-#{TAG_ID_KEY}=#{tagid}]").addClass('selected')
