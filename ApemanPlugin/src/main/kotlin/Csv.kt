import org.apache.commons.csv.CSVFormat
import org.jetbrains.research.groups.ml_methods.utils.ExtractionCandidate
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class Csv(header: Collection<String>, data: Collection<Collection<String>>) {

    val header = ArrayList<String>()
    val data = ArrayList<ArrayList<String>>()

    init {
        header.toCollection(this.header)
        for (collection in data) {
            this.data.add(ArrayList(collection))
        }
    }

    fun export(filepath: String) {

        File(filepath).writer().use { out ->
            val format = CSVFormat.RFC4180.withHeader(*header.toTypedArray())
            format.print(out).printRecords(data)
        }
    }

    fun addIndicesColumn(columnName: String, indices: Collection<String>) {
        assert(data.size == indices.size)

        header.add(0, columnName)
        for ((arr, elemToAdd) in data.zip(indices)) {
            arr.add(0, elemToAdd)
        }
    }

    fun remainColumns(columnNames: ArrayList<String>) {

        assert(header.containsAll(columnNames))

        val columnsIndices = ArrayList<Int>()
        columnNames.forEach { columnsIndices.add(header.indexOf(it)) }

        header.clear()
        header.addAll(columnNames)

        for (arr in data) {
            val temp = ArrayList<String>()
            for (i in columnsIndices)
                temp.add(arr[i])

            arr.clear()
            arr.addAll(temp)

            assert(arr.size == header.size)
        }
    }
}

fun importCsvFrom(filepath: String): Csv {

    File(filepath).reader().use { input ->
        val records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(input)

        val header = ArrayList<String>()
        header.addAll(records.headerMap.keys)

        val data = ArrayList<ArrayList<String>>()
        for (record in records) {
            data.add(ArrayList(record.toMutableList()))
        }

        return Csv(header, data)
    }
}

fun importCsvFrom(candToFeatures: HashMap<ExtractionCandidate, FeatureVector>, featureNames: ArrayList<String>): Csv
{

    val data = ArrayList<ArrayList<String>>()

    for (features in candToFeatures.values) {
        val featuresStr = ArrayList(features.map { it.toString() })
        data.add(featuresStr)
    }
    val csv = Csv(featureNames, data)
    val candidateNames = ArrayList(candToFeatures.keys.map { it.toString() })

    csv.addIndicesColumn("Names", candidateNames)

    return csv
}
