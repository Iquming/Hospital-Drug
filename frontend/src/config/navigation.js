import {
  BookOpen,
  ChartNoAxesCombined,
  ClipboardCheck,
  ClipboardList,
  LayoutDashboard,
  PackageCheck,
  RadioTower,
  Users
} from 'lucide-vue-next'

export const roleLabels = {
  ADMIN: '系统管理员',
  PHARMACIST: '药师',
  NURSE: '护士'
}

export const navigationGroups = [
  {
    id: 'workspace',
    label: '工作台',
    modules: [
      {
        id: 'dashboard',
        label: '运营总览',
        description: '风险、库存与业务动态',
        icon: LayoutDashboard,
        roles: ['ADMIN', 'PHARMACIST', 'NURSE']
      },
      {
        id: 'hisApplications',
        label: '处方调剂',
        description: 'HIS 申请单审核与发药',
        icon: ClipboardList,
        roles: ['ADMIN', 'PHARMACIST', 'NURSE']
      }
    ]
  },
  {
    id: 'pharmacy',
    label: '药事管理',
    modules: [
      {
        id: 'pharmacy',
        label: '药库质控',
        description: '入库、损耗与拆零',
        icon: PackageCheck,
        roles: ['ADMIN', 'PHARMACIST']
      },
      {
        id: 'catalog',
        label: '药品档案',
        description: '药品基础信息维护',
        icon: BookOpen,
        roles: ['ADMIN', 'PHARMACIST']
      },
      {
        id: 'inventory',
        label: '库存盘点',
        description: '实物扫描与差异核对',
        icon: ClipboardCheck,
        roles: ['ADMIN', 'PHARMACIST']
      }
    ]
  },
  {
    id: 'governance',
    label: '系统治理',
    modules: [
      {
        id: 'hisIntegration',
        label: 'HIS 联调',
        description: '模拟申请与回传监测',
        icon: RadioTower,
        roles: ['ADMIN']
      },
      {
        id: 'audit',
        label: '审计报表',
        description: '操作日志与报表导出',
        icon: ChartNoAxesCombined,
        roles: ['ADMIN']
      },
      {
        id: 'users',
        label: '用户管理',
        description: '账号、角色与状态',
        icon: Users,
        roles: ['ADMIN']
      }
    ]
  }
]

export const allModules = navigationGroups.flatMap(group => group.modules)

export const moduleById = id => allModules.find(module => module.id === id)

export const modulesForRole = role => allModules.filter(module => module.roles.includes(role))

export const groupsForRole = role => navigationGroups
  .map(group => ({
    ...group,
    modules: group.modules.filter(module => module.roles.includes(role))
  }))
  .filter(group => group.modules.length)

export const landingModuleByRole = {
  ADMIN: 'dashboard',
  PHARMACIST: 'hisApplications',
  NURSE: 'hisApplications'
}
