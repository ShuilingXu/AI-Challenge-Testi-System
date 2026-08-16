package com.autohr.modules.hr.service;

public final class PayrollExportTemplate {
    private PayrollExportTemplate() { }

    public static final String[] HEADERS = {
            "工号", "姓名", "证照类型", "证照号码", "本期收入", "本期免税收入",
            "基本养老保险费", "基本医疗保险费", "失业保险费", "住房公积金",
            "子女教育", "继续教育", "住房贷款利息", "住房租金", "赡养老人",
            "婴幼儿照护", "其他扣除", "应纳税额"
    };
}
