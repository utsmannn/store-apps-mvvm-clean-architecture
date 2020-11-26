package com.utsman.data.source

import androidx.paging.PagingSource
import com.utsman.abstraction.ext.logi
import com.utsman.data.const.StringValues
import com.utsman.data.model.Category
import com.utsman.data.model.dto.AppsSealedView
import com.utsman.data.model.dto.CategorySealedView
import com.utsman.data.model.dto.CategorySealedView.CategoryView
import com.utsman.data.model.dto.toAppsBannerView
import com.utsman.data.model.dto.toCategoryBannerView
import com.utsman.data.repository.AppsRepository
import com.utsman.data.repository.CategoriesRepository
import com.utsman.data.repository.InstalledAppsRepository
import com.utsman.network.toJson

class CategoriesPagingSource(
    private val categoryRepository: CategoriesRepository,
    private val appsRepository: AppsRepository,
    private val installedAppsRepository: InstalledAppsRepository
) : PagingSource<Int, CategorySealedView>() {

    private val page = 0

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CategorySealedView> {
        return try {
            val currentPage = params.key ?: page
            val data = categoryRepository.getCategoriesView(currentPage) ?: emptyList()
            val newData = data.apply {
                this.map {
                    when (it) {
                        is CategoryView -> {
                            it.apps.map { ap ->
                                when (ap) {
                                    is AppsSealedView.AppsView ->
                                        installedAppsRepository.checkInstalledApps(ap)
                                    else -> ap
                                }
                            }
                        }
                        else -> it
                    }
                }
            }

            val prevPage = if (currentPage <= 0) null else currentPage - 1
            val nextPage = if (!newData.isNullOrEmpty()) currentPage + 2 else null

            if (currentPage == 0) {
                // if current page == 0, add top apps category and covid apps category (from search)

                val covidAppsCategory = Category.simple {
                    this.name = StringValues.covidTitle
                    this.query = "covid"
                    this.image = StringValues.covidBannerUrl
                    this.desc = StringValues.covidDesc
                }
                val covidAppsCategoryView = appsRepository.getSearchApps("covid", 0)
                    .toCategoryBannerView(covidAppsCategory)
                    ?: CategorySealedView.CategoryBannerView()

                val topAppsCategoryView = appsRepository.getTopApps()
                    .datalist?.list?.map { app ->
                        app.toAppsBannerView()
                    } ?: emptyList<AppsSealedView>()

                val categoryView = CategoryView.simple {
                    name = "Top downloads"
                    query = null
                    apps = topAppsCategoryView
                }

                // push random apps category in top of list
                val reversed = newData.toMutableList().asReversed().apply {
                    add(categoryView)
                    add(covidAppsCategoryView)
                }.apply {
                    reverse()
                }.toList()

                LoadResult.Page(reversed, prevPage, nextPage)
            } else {
                LoadResult.Page(newData, prevPage, nextPage)
            }
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }
}