/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 100.0, "KoPercent": 0.0};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.891399416909621, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "文件下载路径 - 不存在文件"], "isController": false}, {"data": [1.0, 500, 1500, "前端登录静态页面"], "isController": false}, {"data": [0.546875, 500, 1500, "EP-001 登录 - 空凭据探测"], "isController": false}, {"data": [1.0, 500, 1500, "EP-067 图片验证码 - 非法场景探测"], "isController": false}, {"data": [1.0, 500, 1500, "EP-068 邮件 OTP - 空请求探测"], "isController": false}, {"data": [0.5, 500, 1500, "初始化线程安全计数器"], "isController": false}, {"data": [1.0, 500, 1500, "普通业务 API"], "isController": false}, {"data": [1.0, 500, 1500, "至少两次有效 429 且 trace_id 不同"], "isController": false}, {"data": [1.0, 500, 1500, "分片上传路径 - 不存在任务"], "isController": false}, {"data": [0.6822916666666666, 500, 1500, "EP-071 找回密码申请 - 空请求探测"], "isController": false}, {"data": [1.0, 500, 1500, "文件批量上传路径 - 空请求"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 686, 0, 0.0, 1096.166180758018, 1, 25353, 5.0, 2029.7000000000007, 10181.199999999999, 19538.659999999996, 8.874055676291006, 4.608833307461451, 1.664037032365725], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["文件下载路径 - 不存在文件", 60, 0, 0.0, 4.383333333333333, 2, 11, 4.0, 6.0, 8.0, 11.0, 86.33093525179856, 48.982688848920866, 16.3556654676259], "isController": false}, {"data": ["前端登录静态页面", 60, 0, 0.0, 27.349999999999994, 14, 105, 22.0, 35.599999999999994, 92.19999999999993, 105.0, 57.41626794258374, 36.89443779904307, 8.74700956937799], "isController": false}, {"data": ["EP-001 登录 - 空凭据探测", 96, 0, 0.0, 4681.354166666666, 1, 25353, 76.5, 14636.899999999998, 18416.14999999999, 25353.0, 1.2737501326823053, 0.5701192813926336, 0.23634035665003716], "isController": false}, {"data": ["EP-067 图片验证码 - 非法场景探测", 96, 0, 0.0, 15.5, 1, 51, 13.0, 31.299999999999997, 39.0, 51.0, 1.2820855257886161, 0.6509155935321457, 0.23538288950025374], "isController": false}, {"data": ["EP-068 邮件 OTP - 空请求探测", 96, 0, 0.0, 9.552083333333332, 1, 46, 5.0, 22.89999999999999, 34.29999999999998, 46.0, 1.2820855257886161, 0.566703087021555, 0.2616756590720906], "isController": false}, {"data": ["初始化线程安全计数器", 1, 0, 0.0, 634.0, 634, 634, 634.0, 634.0, 634.0, 634.0, 1.5772870662460567, 0.0, 0.0], "isController": false}, {"data": ["普通业务 API", 60, 0, 0.0, 5.416666666666664, 2, 19, 4.0, 11.0, 14.849999999999987, 19.0, 83.21775312066575, 47.216322815533985, 13.490377947295423], "isController": false}, {"data": ["至少两次有效 429 且 trace_id 不同", 1, 0, 0.0, 50.0, 50, 50, 50.0, 50.0, 50.0, 50.0, 20.0, 1.34765625, 0.0], "isController": false}, {"data": ["分片上传路径 - 不存在任务", 60, 0, 0.0, 4.450000000000001, 3, 9, 4.0, 6.0, 7.0, 9.0, 85.95988538681948, 54.06070916905445, 18.300053724928368], "isController": false}, {"data": ["EP-071 找回密码申请 - 空请求探测", 96, 0, 0.0, 3090.583333333333, 1, 22414, 4.0, 12155.8, 18489.999999999993, 22414.0, 1.2650221378874131, 0.5266035555687328, 0.25572224857685005], "isController": false}, {"data": ["文件批量上传路径 - 空请求", 60, 0, 0.0, 4.650000000000002, 2, 10, 4.0, 7.0, 9.0, 10.0, 84.03361344537815, 52.849264705882355, 15.674238445378151], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": []}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 686, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
