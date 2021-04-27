package sjdb;

import java.util.ArrayList;
import java.util.List;

public class MyTest {

    public static List<List<Integer>> allPossible(List<Integer> intList) {
        // recursion boundary
        if (intList.isEmpty()) {
            List<List<Integer>> addOne = new ArrayList<>();
            addOne.add(new ArrayList<>());
            return addOne;
        }
        // !predList.isEmpty()
        int removeFirst = intList.remove(0);
        List<List<Integer>> res = new ArrayList<>();
        List<List<Integer>> combinations = allPossible(intList);
        for (List<Integer> lackOne : combinations) {
            for (int i = 0; i <= lackOne.size(); i++) {
                List<Integer> tmp = new ArrayList<>(lackOne);
                tmp.add(i, removeFirst);
                res.add(tmp);
            }
        }
        return res;
    }

    public static void main(String[] args) {
        List<Integer> intList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            intList.add(i);
        }
        System.out.println(intList);
        List<List<Integer>> list = allPossible(intList);
        System.out.println(list);
    }

}
