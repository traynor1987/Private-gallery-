package uk.co.traynor.privategallery.core.home

internal data class HomeSearchResult(val label:String,val detail:String,val shortcut:HomeLayout.Shortcut)

internal fun searchPrivateHome(context:android.content.Context,access:uk.co.traynor.privategallery.core.evidence.EvidenceAccess,query:String):List<HomeSearchResult> {
        access.checkCurrent();val q=query.trim().lowercase(java.util.Locale.ROOT)
        if(q.isEmpty())return emptyList()
        val results=mutableListOf<uk.co.traynor.privategallery.core.home.HomeSearchResult>()
        fun add(label:String,detail:String,target:uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut,notes:String="") {
            if((label+" "+notes).lowercase(java.util.Locale.ROOT).contains(q)&&results.size<100)
                results.add(uk.co.traynor.privategallery.core.home.HomeSearchResult(label,detail,target))
        }
        val hidden=access.owner==uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.Owner.HIDDEN
        access.leases.acquire().use {lease ->
            if(hidden) {
                val owner=checkNotNull(access.hiddenSession);val authority=owner.authority()
                val repo=uk.co.traynor.privategallery.core.hidden.HiddenRepository(context,owner)
                repo.items().forEach{add(it.displayName,"Gallery",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.VAULT)}
                repo.collections().forEach{add(it.name,"Collection",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.COLLECTIONS)}
                listOf(false,true).forEach{tor->
                    val apps=uk.co.traynor.privategallery.core.privateapps.HiddenPrivateAppStore(context.noBackupFilesDir,owner,authority,tor,{access.current()},{commit->lease.commit{commit.run()}}).read()
                    apps.apps.forEach{app->add(app.name(),if(tor)"Private App · Tor"else"Private App · VPN",if(tor)uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.TOR_BROWSER else uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.PRIVATE_APPS)}
                }
            }else {
                val repo=uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(context,lease.key(),{action->lease.commit{action()}},{access.current()&&lease.current()})
                repo.items().forEach{add(it.displayName,"Gallery",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.VAULT)}
                repo.collections().forEach{add(it.name,"Collection",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.COLLECTIONS)}
                uk.co.traynor.privategallery.core.privateapps.PrimaryPrivateAppStore(java.io.File(context.filesDir,"private-apps"),lease.key(),{access.current()},{action->lease.commit{action()}}).read().apps.forEach{app->add(app.name(),"Private App",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.PRIVATE_APPS)}
            }
            val catalog=access.store.read()
            catalog.evidence().forEach{add(it.name(),"Evidence",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.EVIDENCE_LOCKER,it.notes()+" "+it.description()+" "+it.ocr().joinToString(" "){ocr->ocr.text()})}
            catalog.incidents().forEach{add(it.title(),"Incident",uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.EVIDENCE_LOCKER,it.notes()+" "+it.description())}
            access.checkCurrent();check(lease.current())
        }
        return results
    }
