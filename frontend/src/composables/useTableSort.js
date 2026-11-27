import { ref } from 'vue'

/**
 * 列表页统一排序 composable（配合后端 sortBy/sortOrder 参数）
 *
 * 用法：
 *   const { sort, handleSortChange, getSortParams } = useTableSort()
 *
 *   <el-table @sort-change="handleSortChange">
 *     <el-table-column prop="orderNo" label="订单编号" sortable="custom" />
 *   </el-table>
 *
 *   async function fetchData() {
 *     const res = await listXxx({ current, size, ...search, ...getSortParams() })
 *   }
 *
 * 说明：
 * - sortable="custom" 表示排序由后端执行（数据库排序，分页正确）
 * - order: 'ascending'→'asc'，'descending'→'desc'，null→取消排序（不传参，走后端默认排序）
 * - 后端 SortHelper 会做列名白名单校验（驼峰自动转下划线），不支持的列自动忽略
 */
export function useTableSort() {
  const sort = ref({ prop: '', order: '' })

  const handleSortChange = ({ prop, order }) => {
    sort.value = { prop: prop || '', order: order || '' }
  }

  const getSortParams = () => {
    if (!sort.value.prop || !sort.value.order) return {}
    return {
      sortBy: sort.value.prop,
      sortOrder: sort.value.order === 'ascending' ? 'asc' : 'desc'
    }
  }

  const resetSort = () => {
    sort.value = { prop: '', order: '' }
  }

  return { sort, handleSortChange, getSortParams, resetSort }
}
